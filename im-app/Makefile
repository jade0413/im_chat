# 微光 Lumo · im-app 常用命令
.PHONY: bootstrap setup proto gen watch run-android run-ios run-macos run-windows clean release-android patch-android

# 全新克隆后一键：生成四端平台目录 + proto + 依赖 + 代码生成
bootstrap:
	bash tool/bootstrap.sh

# 已有平台目录时：装依赖 + 生成 proto + 生成 drift 代码
setup: proto gen
	flutter pub get

# 从 im-proto 生成 Dart protobuf 绑定（详见 tool/generate_proto.sh）
proto:
	bash tool/generate_proto.sh

# 生成 drift / freezed / json / riverpod 代码（*.g.dart / *.freezed.dart）
gen:
	dart run build_runner build --delete-conflicting-outputs

# 开发时监听式生成
watch:
	dart run build_runner watch --delete-conflicting-outputs

run-android:  ; flutter run -d android
run-ios:      ; flutter run -d ios
run-macos:    ; flutter run -d macos
run-windows:  ; flutter run -d windows

# 热更新（Shorebird，仅移动端）
release-android: ; shorebird release android
patch-android:   ; shorebird patch android

clean:
	flutter clean && rm -rf lib/core/proto/generated
