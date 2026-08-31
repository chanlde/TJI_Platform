#!/usr/bin/env python3
"""在真实 Android 手机和喊话器上自动执行双链路压力测试。"""

from __future__ import annotations

import argparse
import glob
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path


PACKAGE = "com.tji.device"
ACTIVITY = ".ui.main.MainActivity"
DEFAULT_DEVICE_SN = "T5TNBFM4Q"
ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class UiNode:
    text: str
    description: str
    checked: bool
    checkable: bool
    clickable: bool
    bounds: tuple[int, int, int, int]

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return ((left + right) // 2, (top + bottom) // 2)


class Adb:
    def __init__(self, executable: str, serial: str) -> None:
        self.executable = executable
        self.serial = serial

    def run(
        self,
        *arguments: str,
        timeout: int = 30,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [self.executable, "-s", self.serial, *arguments],
            text=True,
            capture_output=True,
            timeout=timeout,
        )
        if check and result.returncode != 0:
            details = (result.stderr or result.stdout).strip()
            raise RuntimeError(f"ADB 命令失败：{' '.join(arguments)}：{details}")
        return result

    def shell(self, *arguments: str, timeout: int = 30) -> str:
        return self.run("shell", *arguments, timeout=timeout).stdout


class SerialCapture:
    def __init__(self, port: str | None, baud: int) -> None:
        self.port = port
        self.baud = baud
        self.data = bytearray()
        self.error: str | None = None
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if self.port is None:
            return
        self._thread = threading.Thread(target=self._capture, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2.0)

    def _capture(self) -> None:
        try:
            import serial  # type: ignore

            with serial.Serial(self.port, self.baud, timeout=0.2) as stream:
                while not self._stop.is_set():
                    chunk = stream.read(4096)
                    if chunk:
                        self.data.extend(chunk)
        except Exception as exc:  # 采集失败不能中断手机侧证据收集。
            self.error = str(exc)


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    numbers = [int(item) for item in re.findall(r"\d+", value)]
    if len(numbers) != 4:
        return (0, 0, 0, 0)
    return tuple(numbers)  # type: ignore[return-value]


def dump_nodes(adb: Adb) -> list[UiNode]:
    # MIUI 会把缺少 theme_compatibility.xml 打到 stderr，但仍会成功生成 XML。
    adb.run(
        "shell",
        "uiautomator",
        "dump",
        "/sdcard/tji-speaker-window.xml",
        check=False,
    )
    xml = adb.shell("cat", "/sdcard/tji-speaker-window.xml")
    root = ET.fromstring(xml)
    nodes: list[UiNode] = []
    for element in root.iter("node"):
        attributes = element.attrib
        nodes.append(
            UiNode(
                text=attributes.get("text", ""),
                description=attributes.get("content-desc", ""),
                checked=attributes.get("checked") == "true",
                checkable=attributes.get("checkable") == "true",
                clickable=attributes.get("clickable") == "true",
                bounds=parse_bounds(attributes.get("bounds", "")),
            )
        )
    return nodes


def visible(node: UiNode) -> bool:
    left, top, right, bottom = node.bounds
    return right > left and bottom > top


def find_node(
    nodes: list[UiNode],
    *,
    text: str | None = None,
    description: str | None = None,
) -> UiNode | None:
    for node in nodes:
        if not visible(node):
            continue
        if text is not None and node.text != text:
            continue
        if description is not None and node.description != description:
            continue
        return node
    return None


def tap(adb: Adb, node: UiNode) -> None:
    x, y = node.center
    adb.shell("input", "tap", str(x), str(y))


def screen_geometry(adb: Adb) -> tuple[int, int]:
    size_text = adb.shell("wm", "size")
    match = re.search(r"Physical size:\s*(\d+)x(\d+)", size_text)
    width, height = (1080, 2160)
    if match is not None:
        width, height = int(match.group(1)), int(match.group(2))
    orientation_text = adb.shell("dumpsys", "input")
    orientation_match = re.search(r"SurfaceOrientation:\s*(\d+)", orientation_text)
    if orientation_match is not None and int(orientation_match.group(1)) in (1, 3):
        width, height = height, width
    return width, height


def swipe_up(adb: Adb) -> None:
    width, height = screen_geometry(adb)
    x = width // 2
    adb.shell(
        "input",
        "swipe",
        str(x),
        str(int(height * 0.82)),
        str(x),
        str(int(height * 0.24)),
        "400",
    )
    time.sleep(0.7)


def swipe_down(adb: Adb) -> None:
    width, height = screen_geometry(adb)
    x = width // 2
    adb.shell(
        "input",
        "swipe",
        str(x),
        str(int(height * 0.24)),
        str(x),
        str(int(height * 0.82)),
        "400",
    )
    time.sleep(0.7)


def ensure_control_page(adb: Adb, device_sn: str) -> list[UiNode]:
    nodes = dump_nodes(adb)
    if find_node(nodes, text="按住录音") is not None:
        return nodes
    # monkey 按启动器语义恢复已有任务；直接 am start 会在部分 MIUI 版本上
    # 叠加 MainActivity，进而留下两个麦克风监听协程并污染压力测试结果。
    adb.run(
        "shell",
        "monkey",
        "-p",
        PACKAGE,
        "-c",
        "android.intent.category.LAUNCHER",
        "1",
    )
    time.sleep(1.5)
    for _ in range(18):
        nodes = dump_nodes(adb)
        if find_node(nodes, text="按住录音") is not None:
            return nodes

        # 覆盖安装或权限弹窗关闭后可能恢复在“高级/录音库/文字”标签；
        # 先切回喊话器首页，再按统一入口继续定位。
        if find_node(nodes, text="喊话器") is not None:
            speaker_home = find_node(nodes, text="首页")
            if speaker_home is not None:
                tap(adb, speaker_home)
                time.sleep(1.2)
                swipe_down(adb)
                continue

        login = find_node(nodes, text="登录")
        if login is not None:
            tap(adb, login)
            time.sleep(2.0)
            continue

        device = find_node(nodes, text=device_sn)
        if device is not None:
            tap(adb, device)
            time.sleep(2.0)
            continue

        product = find_node(nodes, description="喊话器")
        if product is not None:
            tap(adb, product)
            time.sleep(2.0)
            continue

        swipe_up(adb)
    raise RuntimeError(f"无法自动进入喊话器 {device_sn} 控制页")


def ensure_monitor_enabled(adb: Adb, nodes: list[UiNode]) -> None:
    for _ in range(8):
        if find_node(nodes, text="设备麦克风监听") is not None:
            break
        swipe_up(adb)
        nodes = dump_nodes(adb)
    else:
        raise RuntimeError("控制页缺少设备麦克风监听区域")
    switches = [node for node in nodes if node.checkable and visible(node)]
    if not switches:
        raise RuntimeError("未找到麦克风监听开关")
    switch = switches[0]
    if not switch.checked:
        tap(adb, switch)
        time.sleep(2.5)
        refreshed = dump_nodes(adb)
        refreshed_switches = [node for node in refreshed if node.checkable and visible(node)]
        if not refreshed_switches or not refreshed_switches[0].checked:
            raise RuntimeError("麦克风监听没有成功开启")


def long_press(adb: Adb, node: UiNode, duration_ms: int) -> None:
    x, y = node.center
    adb.shell(
        "input",
        "swipe",
        str(x),
        str(y),
        str(x),
        str(y),
        str(duration_ms),
        timeout=max(30, duration_ms // 1000 + 10),
    )


def prepare_acoustic_prompt(output_dir: Path, enabled: bool) -> Path | None:
    if not enabled:
        return None
    if shutil.which("say") is None or shutil.which("afplay") is None:
        raise RuntimeError("低音量声学激励需要 macOS say 和 afplay")
    prompt = output_dir / "quiet-acoustic-prompt.aiff"
    phrase = "自动化喊话压力测试，一二三四五六七八九"
    subprocess.run(
        ["say", "-r", "220", "-o", str(prompt), phrase],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return prompt


def start_acoustic_prompt(
    prompt: Path | None,
    volume: float,
) -> subprocess.Popen[str] | None:
    if prompt is None:
        return None
    command = (
        f"sleep 0.25; while :; do afplay -v {volume:.3f} "
        f"{shlex.quote(str(prompt))}; "
        f"sleep 0.10; done"
    )
    return subprocess.Popen(
        ["/bin/zsh", "-c", command],
        text=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def stop_process(process: subprocess.Popen[str] | None) -> None:
    if process is None:
        return
    process.terminate()
    try:
        process.wait(timeout=1.0)
    except subprocess.TimeoutExpired:
        process.kill()


def select_adb_serial(adb_executable: str, requested: str | None) -> str:
    result = subprocess.run(
        [adb_executable, "devices"],
        text=True,
        capture_output=True,
        check=True,
    )
    devices = [
        line.split()[0]
        for line in result.stdout.splitlines()[1:]
        if len(line.split()) >= 2 and line.split()[1] == "device"
    ]
    if requested is not None:
        if requested not in devices:
            raise RuntimeError(f"指定手机未连接：{requested}")
        return requested
    if len(devices) != 1:
        raise RuntimeError(f"需要且只能连接一台手机，当前：{devices or '无'}")
    return devices[0]


def auto_serial_port(requested: str | None) -> str | None:
    if requested == "none":
        return None
    if requested:
        return requested
    candidates = sorted(glob.glob("/dev/cu.usbmodem*"))
    return candidates[0] if len(candidates) == 1 else None


def relevant_error_lines(log: str) -> list[str]:
    lines: list[str] = []
    for line in log.splitlines():
        lowered = line.lower()
        if not any(token in lowered for token in ("speaker", "audiotrack", "audiorecord")):
            continue
        if any(token in lowered for token in ("exception", "failed", "timeout", "underrun")):
            # 小米音频 HAL 的 ACDB 噪声与业务链路无关，不计入 App 错误。
            if "acdb" not in lowered:
                lines.append(line)
    return lines


def serial_text_ratio(data: bytes) -> float:
    if not data:
        return 0.0
    text_bytes = sum(byte in (9, 10, 13) or 32 <= byte <= 126 for byte in data)
    return text_bytes / len(data)


def write_report(
    output_dir: Path,
    args: argparse.Namespace,
    log: str,
    serial_capture: SerialCapture,
) -> tuple[Path, bool]:
    pause_count = len(re.findall(r"mcu_mic_ptt_pause", log))
    resume_count = len(re.findall(r"mcu_mic_ptt_resume", log))
    sent_lines = re.findall(r"direct ptt sent[^\n]+", log)
    feedback_packets = [int(value) for value in re.findall(r"mcu monitor pcm packet=(\d+)", log)]
    errors = relevant_error_lines(log)
    expected = len(args.durations_ms) * args.rounds
    serial_ratio = serial_text_ratio(serial_capture.data)

    # 完整媒体发送需要真实声学激励；控制时序本身必须每轮严格成对。
    control_ok = pause_count == expected and resume_count == expected
    media_ok = len(sent_lines) == expected
    feedback_ok = len(feedback_packets) >= 2 and feedback_packets[-1] > feedback_packets[0]
    passed = control_ok and media_ok and feedback_ok and not errors

    lines = [
        "# 喊话器双链路自动压力测试",
        "",
        f"- 结果：{'通过' if passed else '未通过'}",
        f"- 手机：`{args.adb_serial}`",
        f"- MCU：`{args.device_sn}`",
        f"- 测试轮次：{expected}",
        f"- 声学激励：{'关闭' if args.no_say else f'{args.acoustic_volume:.1%} 低音量'}",
        f"- 按下暂停命令：{pause_count}/{expected}",
        f"- 松手恢复命令：{resume_count}/{expected}",
        f"- 完整 Opus 媒体发送：{len(sent_lines)}/{expected}",
        f"- 回传播放采样点：{feedback_packets[0] if feedback_packets else '无'} -> "
        f"{feedback_packets[-1] if feedback_packets else '无'}",
        f"- App 相关错误：{len(errors)}",
        f"- 串口：`{serial_capture.port or '未启用'}`，{serial_capture.baud} baud",
        f"- 串口字节：{len(serial_capture.data)}，文本可读率：{serial_ratio:.1%}",
    ]
    if serial_capture.error:
        lines.append(f"- 串口采集错误：`{serial_capture.error}`")
    if serial_capture.data and serial_ratio < 0.75:
        lines.append("- 串口判定：当前端口不像 USART1 115200 文本日志，原始数据已保留，不参与通过判定。")
    if not media_ok:
        lines.append("- 媒体判定：未完成全部发送；请把手机麦克风靠近 Mac 扬声器，或现场说话后重跑。")
    if sent_lines:
        lines.extend(["", "## 媒体发送", ""])
        lines.extend(f"- `{line}`" for line in sent_lines)
    if errors:
        lines.extend(["", "## 相关错误", ""])
        lines.extend(f"- `{line}`" for line in errors[:30])

    report = output_dir / "report.md"
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return report, passed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device-sn", default=DEFAULT_DEVICE_SN)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--adb-serial")
    parser.add_argument("--serial-port", help="MCU 文本串口；用 none 禁用")
    parser.add_argument("--serial-baud", type=int, default=115200)
    parser.add_argument("--rounds", type=int, default=2)
    parser.add_argument(
        "--durations-ms",
        type=int,
        nargs="+",
        default=[1500, 3000, 5000],
    )
    parser.add_argument("--settle-ms", type=int, default=2500)
    parser.add_argument("--no-say", action="store_true", help="不使用 Mac 语音作声学激励")
    parser.add_argument(
        "--acoustic-volume",
        type=float,
        default=0.04,
        help="afplay 声学激励增益，默认 0.04（4%%）",
    )
    parser.add_argument("--output-dir", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if shutil.which(args.adb) is None:
        raise RuntimeError(f"找不到 ADB：{args.adb}")
    if (args.rounds < 1 or any(value < 600 for value in args.durations_ms) or
        not 0.0 <= args.acoustic_volume <= 0.10):
        raise RuntimeError("rounds 必须大于 0，单次按住时间不能小于 600 ms")
    args.adb_serial = select_adb_serial(args.adb, args.adb_serial)
    adb = Adb(args.adb, args.adb_serial)
    output_dir = args.output_dir or (
        ROOT / "build" / "speaker-full-duplex-stress" /
        datetime.now().strftime("%Y%m%d-%H%M%S")
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    prompt_file = prepare_acoustic_prompt(output_dir, not args.no_say)

    nodes = ensure_control_page(adb, args.device_sn)
    ensure_monitor_enabled(adb, nodes)
    serial_capture = SerialCapture(
        auto_serial_port(args.serial_port),
        args.serial_baud,
    )
    serial_capture.start()
    adb.run("logcat", "-c")

    try:
        for round_index in range(args.rounds):
            for duration_ms in args.durations_ms:
                nodes = dump_nodes(adb)
                button = find_node(nodes, text="按住录音")
                for _ in range(8):
                    if button is not None:
                        break
                    swipe_down(adb)
                    nodes = dump_nodes(adb)
                    button = find_node(nodes, text="按住录音")
                if button is None:
                    raise RuntimeError("压力测试中丢失按住录音按钮")
                print(
                    f"round={round_index + 1}/{args.rounds} "
                    f"durationMs={duration_ms}",
                    flush=True,
                )
                prompt = start_acoustic_prompt(
                    prompt_file,
                    args.acoustic_volume,
                )
                try:
                    long_press(adb, button, duration_ms)
                finally:
                    stop_process(prompt)
                time.sleep(args.settle_ms / 1000.0)
    finally:
        time.sleep(1.0)
        serial_capture.stop()

    pid = adb.shell("pidof", PACKAGE).strip()
    log_arguments = ["logcat", "-d", "-v", "threadtime"]
    if pid:
        log_arguments.append(f"--pid={pid}")
    log = adb.run(*log_arguments).stdout
    (output_dir / "android-logcat.txt").write_text(log, encoding="utf-8")
    (output_dir / "mcu-serial.bin").write_bytes(serial_capture.data)
    (output_dir / "mcu-serial.txt").write_text(
        serial_capture.data.decode("utf-8", errors="replace"),
        encoding="utf-8",
    )
    report, passed = write_report(output_dir, args, log, serial_capture)
    print(f"report={report}")
    print(f"result={'PASS' if passed else 'FAIL'}")
    return 0 if passed else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.SubprocessError, ET.ParseError) as exc:
        print(f"result=ERROR message={exc}", file=sys.stderr)
        sys.exit(2)
