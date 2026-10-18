### DSU Sideloader v3.1 (10)

- **Dynamic A/B Slot Dual-Boot Environment**: Fully dynamic active slot detection (`_a`/`_b`) without hardcoding, automatically establishing the secondary OS environment on the opposite slot.
- **Interactive Quick Settings Tile Switcher**: One-tap toggle directly switches active slots and reboots between Primary and Secondary OS with a confirmation dialog.
- **OTA Payload Partition Customizer**: Interactive partition inspector and selector for OTA ROM payloads (Evolution X, LineageOS, PixelOS, etc.), categorizing into Physical Boot Partitions and Logical Dynamic Partitions.
- **Automated Secondary Boot Partitions Backup & Restore**: Automatically backs up physical boot partitions (`boot`, `init_boot`, `vendor_boot`, `dtbo`, etc.) before flashing and restores them cleanly upon discarding.
- **OTA Full ROM Package Installer**: Native high-speed extraction and flashing of complex multi-partition OTA packages via direct low-level DSU APIs.
- **UI Performance & High Refresh Rate**: Optimized background file inspection and silky smooth 120Hz/144Hz scroll responsiveness.
- **Dual Boot Settings Integration**: Automatically locks and greys out conflicting persistent boot preferences when dual boot mode is engaged.

Read more at: https://github.com/CuriousNom/DSU-Sideloader/releases/tag/3.1