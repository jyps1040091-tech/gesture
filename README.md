# 手勢滑鼠（Android 版）

電腦版 `gesture_mouse.py` 的 Android 最小可用版（Kotlin）。

## 建置

1. 用 Android Studio（Koala 以上）開啟本資料夾，等 Gradle 同步。
2. 第一次建置會自動下載手部模型到 `app/src/main/assets/hand_landmarker.task`。
   若下載失敗，手動下載後放到同一個位置：
   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
3. 接上手機（開啟 USB 偵錯）按 Run。minSdk 26（Android 8.0），截圖需要 Android 9+。

## 使用

1. 開 App → ① 授予相機 / 通知權限。
2. ② 到「設定 → 無障礙」找到「手勢滑鼠」並開啟。
3. ③ 啟動。預設是「停用」狀態，雙手攤開 1 秒或按通知列「啟動 / 停用」才會開始控制。

## 檔案對照

| 電腦版 | Android 版 |
|---|---|
| `classify`、`is_thumbs_up`、`is_c_shape`、`is_l_shape`、`hand_pose` | `Gestures.kt` |
| `OneEuro` | `OneEuro.kt` |
| `main()` 的操作邏輯 | `GestureEngine.kt` |
| `pyautogui` 點擊 / 拖曳 / 捲動 | `GestureAccessibilityService.kt`（`dispatchGesture`） |
| `Cursor` + `Overlay` | `CursorView.kt`（Choreographer 插值） |
| 攝影機 + MediaPipe 迴圈 | `CameraService.kt`（前景服務） |

## 手勢對應（和電腦版不同的地方）

- 右鍵 → 長按
- 比讚 → 返回（電腦版是 👍 彈幕）
- 雙手握拳 2 秒 → 回桌面（電腦版是開虛擬選單）
- F8 / F9 → 通知列按鈕
- 沒有移植：虛擬選單、螢幕錄影、鍵盤 RGB、人臉辨識、彈幕

## 已知限制

- 目前假設直式；橫放需要重新啟動服務才會抓到新的螢幕尺寸。
- Android 10+ 對「背景啟動 Activity」有限制，C 形開 Chrome 在部分機型可能被擋。
- 捲動是一次次的滑動手勢（不是連續滾輪），手感用 `SCROLL_DEADZONE`、`SCROLL_GAIN` 調整。
- 銀行 / 金融類 App 可能偵測到無障礙服務而拒絕運作。
- 持續開相機＋模型推論會發熱耗電，不用時請從通知列結束。

## 不裝 Android Studio：用 GitHub 雲端建置 APK

1. 註冊 / 登入 GitHub，建立一個新的 repository。
2. 把本資料夾的所有檔案（包含隱藏的 `.github` 資料夾）上傳到 repository（網頁的 Add file → Upload files）。
3. 到 repository 的 Actions 分頁，等 build-apk 跑完（約 5～10 分鐘）。
4. 點進該次執行，下方 Artifacts 下載 gesture-mouse-apk，解壓得到 app-debug.apk。
5. 傳到手機安裝（需允許「安裝未知來源 App」），再依上面的「使用」步驟操作。
