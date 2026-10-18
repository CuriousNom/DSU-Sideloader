### DSU Sideloader v3.1 (10)

#### 🔀 Dynamic Dual Boot & Physical Slot Management
- **Full Dual Boot Dashboard**: Real-time status cards showing running OS, active slot (`_a`/`_b`), guest ROM name, and dynamic Android version detection.
- **Dynamic Slot Detection**: Fully dynamic slot management without hardcoded slots, automatically configuring the inactive slot for the secondary guest OS.
- **Automated Physical Boot Slot Flashing**: Flashes boot, vendor_boot, init_boot, and dtbo to secondary slot for booting custom ROMs without touching host OS.
- **Automated Boot Partitions Backup & Restore**: Safeguards physical boot partitions by backing them up to `/sdcard/DSU_Backup/` before flashing and restoring upon discard.
- **Seamless Slot & OS Switcher**: Instant switching and rebooting between Primary and Secondary OS with automated slot verification.
- **One-Shot Auto-Return & Sticky Mode**: Option to auto-return to primary slot on reboot, or enforce persistent boot with interactive Sticky Mode toggle.
- **Unrooted Guest OS Safety Guards**: Guided Fastboot switching instructions with one-tap clipboard copy when guest OS lacks root.
- **Guest OS Notification Helper**: Ongoing foreground notification with "Reboot to Primary OS" action to prevent boot hangs.
- **Official Notification Suppression**: Suppresses official reboot notifications that could cause boot hangs on vendor ROMs.

#### 📦 Full OTA ROM & Payload.bin Support
- **Direct OTA Package & Payload.bin Flashing**: Native support to extract and install complete OTA packages (Evolution X, LineageOS, PixelOS, etc.) directly into DSU.
- **Payload Partition Customizer**: Interactive bottom sheet customizer to inspect partition breakdown with individual checkboxes and sizes.
- **Streaming Extraction & Memory Optimization**: Memory-efficient streaming extraction preventing `OutOfMemoryError` on large multi-gigabyte payloads.
- **Smart Partition Whitelist & Filtering**: Filters out firmware/empty partitions (`aop`, `unsparse_super_empty`) and extracts only supported dynamic and boot partitions.
- **SAF Document URI Resolution**: Transparently resolves Storage Access Framework (SAF) URIs to real filesystem paths for extraction.
- **Rollback Workspace Cleanup**: Automatically cleans up temporary extraction workspace on cancellation or error.

#### 🎛️ UI, UX & Interaction Enhancements
- **Interactive Quick Settings Tile Switcher**: One-tap QS tile action to switch active slots and reboot between Primary and Secondary OS with a confirmation dialog.
- **System Haptic Feedback**: Subtle haptic vibration feedback integrated into buttons, switches, chips, and preference items, with a toggle in Settings.
- **Preset Userdata Chips**: Quick allocation chips (+4GB, +8GB, +16GB, +32GB) for instant userdata sizing.
- **Modernized Installation Progress**: Real-time progress bar with percentage pill badge and current partition extraction/flashing status.
- **Operation Mode TopBar Badge**: Visual pill badge in the TopBar indicating current active mode (Root, Shizuku, ADB).
- **One-Tap Share Action**: Integrated Android Share sheet to instantly share generated ADB installation commands and scripts.
- **Tonal Section Badges & Visual Polish**: Card titles enhanced with tonal icon containers and subtle inset preference dividers.
- **Contextual UI Cleanups**: Eliminated duplicate reboot and discard buttons in installation section during dual boot.
- **Dual Boot Settings Integration**: Automatically locks and greys out conflicting persistent boot preferences in Settings when dual boot mode is engaged.

#### ⚡ Performance, Core & Architecture Optimizations
- **App Startup Speed & Responsiveness**: Eliminated blocking shell calls and service hangs during app launch for instant opening.
- **Silky Smooth 120Hz/144Hz Scrolling**: Asynchronous header inspection and non-blocking background initialization.
- **Direct Root Installer Modernization**: Low-level `IGsiService` and `gsid` IPC implementation bypassing staging and recompression for high-speed installations.
- **Image & Filesystem Header Inspector**: Asynchronous file header inspection supporting EROFS, ext4, Android Sparse (`simg`), and archives (`.xz`, `.gz`, `.zip`).
- **16 KB Memory Page Size Support**: Detection and advisories for modern 16 KB kernel architectures on Android 15+.
- **Package Name Migration**: Updated application ID to `curiousnom.dsusideloader`.

Read more at: https://github.com/CuriousNom/DSU-Sideloader/releases/tag/3.1