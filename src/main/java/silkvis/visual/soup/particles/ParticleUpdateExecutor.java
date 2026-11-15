package silkvis.visual.soup.particles;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ported from SoupVisuals {@code padej.soup.base.util.particle.ParticleUpdateExecutor} (Padej_).
 *
 * <p>In-place update with removal: iterating with an {@link Iterator} and
 * {@code remove()} means no garbage per tick, which is the whole point of this helper in
 * the original (thousands of ambient particles). The "on remove" callback lets the modules
 * drop a particle out of the spatial grid in the same pass.</p>
 */
public final class ParticleUpdateExecutor {
    private ParticleUpdateExecutor() {
    }

    public static <T extends AbstractParticle> void updateParticlesInPlace(List<T> particles,
                                                                          long currentTime,
                                                                          Predicate<T> visibility) {
        updateParticlesInPlace(particles, currentTime, visibility, null, null);
    }

    public static <T extends AbstractParticle> void updateParticlesInPlace(List<T> particles,
                                                                          long currentTime,
                                                                          Predicate<T> visibility,
                                                                          Consumer<T> collisionSource,
                                                                          Consumer<T> onRemove) {
        if (particles.isEmpty()) {
            return;
        }
        Iterator<T> iterator = particles.iterator();
        while (iterator.hasNext()) {
            T particle = iterator.next();
            boolean visible = visibility == null || visibility.test(particle);
            if (particle.update(currentTime, visible)) {
                iterator.remove();
                if (onRemove != null) {
                    onRemove.accept(particle);
                }
            }
        }
    }
}
