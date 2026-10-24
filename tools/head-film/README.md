# 八颗头影片制作入口

所有素材与结果在 `video/intro/eight-heads/`。此目录只放可维护脚本、隔离的拍摄客户端和回放兼容 mixin；不会进入正常发布的模组 JAR。

## 重做后期

在模组根目录执行：

```sh
/Users/nakami/Documents/code/mambo-tts/.venv/bin/python tools/head-film/edit.py
python3 tools/head-film/quality.py
```

`latest-take.txt` 指向主体表演，`source-overrides.json` 指向山羊补拍。后期读取 `narration-timeline.json`，使用真实音频时长；代码、时间表、镜头路径/大小/修改时间参与缓存键。成片 5965 帧、30 fps。字幕 PNG 使用系统 Hiragino Sans GB 字体，另存逐句 SRT。

`voice.py` 使用本地曼波服务 `http://127.0.0.1:9881/tts`。本次 speed=1.08、seed=42；逐句音频和参数已经缓存，重新剪辑无需启动服务。

## 实拍和回放导出

```sh
./gradlew -I tools/head-film/init.gradle runHeadFilm --offline -PfilmMode=pilot
./gradlew -I tools/head-film/init.gradle runHeadFilm --offline -PfilmMode=capture
./gradlew -I tools/head-film/init.gradle runHeadFilm --offline -PfilmMode=capture -PfilmScenes=goat
./gradlew -I tools/head-film/init.gradle runHeadFilm --offline -PfilmMode=render -PfilmTake=/absolute/path/to/take -PfilmOnly=goat
```

每次 capture 新建隔离世界，不使用玩家存档。按需布景、摆放目标和提供物品，通过模组正常服务端入口触发效果。演示目标有固定位置及较高生命值，不作为正常生存战斗数值测试。

`render` 根据同目录 `replay.txt` 打开 Flashback 表演，重新生成机位计划，跳过已经存在的 MP4。要改某一机位，先把该 MP4 移到备份目录再运行。不要覆盖 `latest-take.txt` 的主体来源后直接后期，补拍应写入 `source-overrides.json`。

Flashback 导演版本与嵌套依赖保存在 `video/intro/eight-heads/runtime/`，SHA-256 见 manifest。开发运行必须显式添加嵌套依赖。构建输出在 `build/head-film/`，游戏运行目录在影片 `run/` 下。

`ReplayWearMixin` 只阻止回放服务器重新计算 WearHandler 状态，保留录制玩家同步过来的鼓肚状态。正式模组逻辑未改。短样已验证前跳、回跳、再次前跳后的鼓肚状态；这不是所有模组状态的通用兼容保证。

## 技术检查

```sh
python3 /Users/nakami/.agents/skills/minecraft-mod-video/scripts/verify_media.py video/intro/eight-heads/final/头部功能_八颗头_曼波_1080p30.mp4 --width 1920 --height 1080 --fps 30 --duration 198.83333333333334 --require-audio
```

输出的完整解码和响度报告不能替代视觉检查及听检。本次工具不能消费音频，未完成听检；交付时须保留这项说明。片尾 MV 只有挂头衔接及 2 秒占位，尚未制作 MV 的音乐/镜头。
