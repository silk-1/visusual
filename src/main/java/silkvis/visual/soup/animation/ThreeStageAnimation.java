package silkvis.visual.soup.animation;

/**
 * Ported from SoupVisuals {@code padej.soup.base.util.animation.ThreeStageAnimation} (Padej_).
 *
 * <p>Three-stage envelope (appear / exist / disappear) used by HitBubbles. The negative
 * "exist" branch is a real feature of the original (appear and disappear overlap), so it
 * is kept verbatim instead of being simplified away.</p>
 */
public final class ThreeStageAnimation {
    public enum AnimationStage {
        APPEAR,
        EXIST,
        DISAPPEAR,
        FINISHED
    }

    private final double appearDuration;
    private final double existDuration;
    private final double disappearDuration;
    private final double totalDuration;
    private final Interpolation appearInterpolation;
    private final Interpolation disappearInterpolation;

    public ThreeStageAnimation(double appearDuration, double existDuration, double disappearDuration,
                               Interpolation appearInterpolation, Interpolation disappearInterpolation) {
        this.appearDuration = appearDuration;
        this.existDuration = existDuration;
        this.disappearDuration = disappearDuration;
        this.totalDuration = appearDuration + Math.max(0.0, existDuration) + disappearDuration;
        this.appearInterpolation = appearInterpolation == null ? Interpolations.LINEAR : appearInterpolation;
        this.disappearInterpolation =
                disappearInterpolation == null ? Interpolations.LINEAR : disappearInterpolation;
    }

    public double getValue(double elapsedTime) {
        if (elapsedTime >= this.totalDuration) {
            return 0.0;
        }
        if (this.existDuration < 0.0) {
            double overlapStart = this.appearDuration + this.existDuration;
            if (elapsedTime <= overlapStart) {
                return this.appearInterpolation.interpolate(elapsedTime / this.appearDuration);
            } else if (elapsedTime <= this.appearDuration) {
                double appearValue = this.appearInterpolation.interpolate(elapsedTime / this.appearDuration);
                double disappearProgress = (elapsedTime - overlapStart) / this.disappearDuration;
                double disappearValue = 1.0 - this.disappearInterpolation.interpolate(disappearProgress);
                return Math.min(appearValue, disappearValue);
            }
            double disappearProgress = (elapsedTime - overlapStart) / this.disappearDuration;
            return 1.0 - this.disappearInterpolation.interpolate(disappearProgress);
        }
        if (elapsedTime <= this.appearDuration) {
            return this.appearInterpolation.interpolate(elapsedTime / this.appearDuration);
        }
        if (elapsedTime <= this.appearDuration + this.existDuration) {
            return 1.0;
        }
        double progress = (elapsedTime - this.appearDuration - this.existDuration) / this.disappearDuration;
        return 1.0 - this.disappearInterpolation.interpolate(progress);
    }

    public AnimationStage getStage(double elapsedTime) {
        if (this.existDuration < 0.0) {
            double overlapStart = this.appearDuration + this.existDuration;
            if (elapsedTime <= overlapStart) {
                return AnimationStage.APPEAR;
            } else if (elapsedTime <= this.appearDuration) {
                return AnimationStage.APPEAR;
            }
            return elapsedTime <= this.totalDuration ? AnimationStage.DISAPPEAR : AnimationStage.FINISHED;
        }
        if (elapsedTime <= this.appearDuration) {
            return AnimationStage.APPEAR;
        } else if (elapsedTime <= this.appearDuration + this.existDuration) {
            return AnimationStage.EXIST;
        }
        return elapsedTime <= this.totalDuration ? AnimationStage.DISAPPEAR : AnimationStage.FINISHED;
    }

    public boolean isFinished(double elapsedTime) {
        return elapsedTime >= this.totalDuration;
    }

    public double getAppearDuration() {
        return this.appearDuration;
    }

    public double getExistDuration() {
        return this.existDuration;
    }

    public double getDisappearDuration() {
        return this.disappearDuration;
    }

    public double getTotalDuration() {
        return this.totalDuration;
    }
}
