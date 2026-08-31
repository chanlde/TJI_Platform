# Native 第三方源码

本目录保存 native 构建必须、且需要在离线或干净环境中可重复使用的第三方源码。

## libopus

- 版本：1.6.1
- 官方来源：<https://downloads.xiph.org/releases/opus/opus-1.6.1.tar.gz>
- 官方源包 SHA256：`6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1`
- 许可证：BSD 3-Clause 风格许可证，完整文本见 `opus/COPYING`
- 仓库内容：官方源包的 CMake 构建所需精简子集

当前精简子集已与上述官方源包逐文件比较；仓库内保留的文件内容一致，仅省略本项目不使用的文档、Autotools、Meson、DNN 和上游测试资产。

`native/speaker-core/CMakeLists.txt` 会在配置阶段检查 `package_version`，版本不是 1.6.1 或文件缺失时立即停止构建。

升级规则：

1. 只从 Opus 官方站点下载明确版本的发布源包。
2. 先核对官方公布的 SHA256，再解压源包。
3. 更新精简子集时不得修改 Opus 源文件内容。
4. 同步更新本文件的版本、来源和摘要。
5. 运行 native CTest、map/noMap JVM 测试和两个 flavor 的 Android 构建。
6. 真机确认 Speaker 喊话播放与反馈试听无回归后才能完成升级。
