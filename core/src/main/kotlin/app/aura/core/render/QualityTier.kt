package app.aura.core.render

/** 3D scene quality, matching the tiers in Aura's settings. */
enum class QualityTier(
    val targetFps: Int,
    val rainDrops: Int,
    val snowFlakes: Int,
    val cloudPuffs: Int,
    val reflections: Boolean,
) {
    HIGH(targetFps = 60, rainDrops = 4000, snowFlakes = 2600, cloudPuffs = 196, reflections = true),
    BALANCED(targetFps = 60, rainDrops = 2000, snowFlakes = 1400, cloudPuffs = 100, reflections = true),
    ECO(targetFps = 30, rainDrops = 700, snowFlakes = 500, cloudPuffs = 49, reflections = false),
}

data class DeviceProfile(
    val totalRamMb: Int,
    val isLowRamDevice: Boolean,
    val isPowerSaveMode: Boolean,
    val batteryPercent: Int,
    val reduceMotion: Boolean,
)

object QualityPolicy {
    /**
     * Picks the automatic tier. Budget phones and power saving get the economy scene;
     * a user's explicit choice in settings always wins over this.
     */
    fun automatic(device: DeviceProfile): QualityTier = when {
        device.reduceMotion -> QualityTier.ECO
        device.isLowRamDevice || device.totalRamMb < 3_000 -> QualityTier.ECO
        device.isPowerSaveMode || device.batteryPercent in 0..15 -> QualityTier.ECO
        device.totalRamMb < 6_000 -> QualityTier.BALANCED
        else -> QualityTier.HIGH
    }
}
