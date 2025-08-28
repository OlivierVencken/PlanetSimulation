package io.github.simulation.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

/**
 * Procedural starfield renderer.
 * Generates a set of stars placed on random directions at a large radius.
 */
public class StarfieldRenderer {
    private final int starCount;
    private final Array<Vector3> dirs = new Array<>();
    private final float[] sizes;
    private final float[] brightness;

    private SpriteBatch batch;
    private Texture starTexture;
    private OrthographicCamera orthoCam;

    private final Random rng = new Random(12345);
    private float starRadius = 50000f;

    // Flicker control
    private boolean enableFlicker = true;
    private float flickerSpeed = 2.0f; // Hz
    private float time = 0f;

    public StarfieldRenderer() {
        this(10000);
    }

    public StarfieldRenderer(int starCount) {
        this.starCount = Math.max(0, starCount);
        this.sizes = new float[this.starCount];
        this.brightness = new float[this.starCount];
    }

    /**
     * Create internal resources and generate stars. Call once from create().
     */
    public void create(Camera worldCamera) {
        // adopt radius from the camera far plane if possible
        if (worldCamera != null) {
            starRadius = Math.max(1000f, worldCamera.far * 0.9f);
        }
        create();
    }

    public void create() {
        batch = new SpriteBatch();
        orthoCam = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        orthoCam.setToOrtho(false);

        // generate a tiny circular 'glow' texture for stars
        starTexture = createStarPixmapTexture(32);

        // generate stars placed on a sphere of radius `starRadius`
        dirs.clear();
        for (int i = 0; i < starCount; i++) {
            // uniform direction on sphere (unit vector)
            float u = rng.nextFloat() * 2f - 1f;
            float phi = rng.nextFloat() * MathUtils.PI2;
            float sinTheta = (float) Math.sqrt(1 - u * u);
            float x = sinTheta * MathUtils.cos(phi);
            float y = sinTheta * MathUtils.sin(phi);
            float z = u;

            Vector3 dir = new Vector3(x, y, z).nor();
            dirs.add(dir);

            // keep size/brightness generation as before
            sizes[i] = 1f + rng.nextFloat() * 2.5f;
            brightness[i] = 0.1f + rng.nextFloat() * 0.9f;
        }
    }

    /**
     * Call once per frame. Renders the starfield using the provided world camera.
     * Must be called after Gdx.gl.glClear(...) and before 3D objects are rendered
     * (so depth test can be disabled).
     */
    public void render(com.badlogic.gdx.graphics.Camera worldCamera) {
        if (batch == null || starTexture == null || worldCamera == null) {
            return;
        }

        time += Gdx.graphics.getDeltaTime();

        // update ortho camera to screen size (in case of resize)
        if (orthoCam.viewportWidth != Gdx.graphics.getWidth() || orthoCam.viewportHeight != Gdx.graphics.getHeight()) {
            orthoCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            orthoCam.update();
        }

        // prepare GL state for additive, no-depth sprite rendering
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        batch.setProjectionMatrix(orthoCam.combined);
        batch.begin();

        Vector3 tmp = new Vector3();
        for (int i = 0; i < dirs.size; i++) {
            Vector3 dir = dirs.get(i);

            // compute world position relative to camera each frame:
            tmp.set(dir).scl(starRadius).add(worldCamera.position);

            // project to screen coords and continue using tmp
            worldCamera.project(tmp);

            // compute flicker multiplier
            float flick = 1f;
            if (enableFlicker) {
                // simple per-star phase using RNG seeded deterministically from position
                float phase = (float) ((dir.x * 73428791L + dir.y * 91278361L + dir.z * 16273841L) % 10000) / 10000f;
                float t = (time * flickerSpeed + phase * MathUtils.PI2);
                flick = 0.75f + 0.25f * (0.5f + 0.5f * MathUtils.sin(t));
            }

            float b = brightness[i] * flick;
            float size = sizes[i] * (1f + 0.1f * (1f - b));

            // skip stars that projected off-screen (cheap bounds check)
            if (tmp.x < -64 || tmp.x > Gdx.graphics.getWidth() + 64 || tmp.y < -64
                    || tmp.y > Gdx.graphics.getHeight() + 64) {
                continue;
            }

            float drawSize = size * 2f; // scale up for visibility; tweak to taste
            float x = tmp.x - drawSize * 0.5f;
            float y = tmp.y - drawSize * 0.5f;

            batch.setColor(1f, 1f, 1f, MathUtils.clamp(b, 0f, 1f));
            batch.draw(starTexture, x, y, drawSize, drawSize);
        }

        batch.end();

        // restore GL state
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private Texture createStarPixmapTexture(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        int cx = size / 2;
        int cy = size / 2;
        float maxR = size / 2f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx + 0.5f;
                float dy = y - cy + 0.5f;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / maxR;
                float alpha = 0f;
                if (d <= 1f) {
                    alpha = (float) Math.pow(1f - d, 2.2f);
                }
                // use setColor so libGDX packs channels correctly for the platform
                p.setColor(1f, 1f, 1f, alpha);
                p.drawPixel(x, y);
            }
        }

        Texture t = new Texture(p);
        p.dispose();
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return t;
    }

    public void dispose() {
        if (batch != null) {
            batch.dispose();
        }
        if (starTexture != null) {
            starTexture.dispose();
        }
        dirs.clear();
    }

    /**
     * Regenerate the star positions. Useful if camera far/scale changed.
     */
    public void regenerate(Camera worldCamera) {
        if (worldCamera != null) {
            starRadius = Math.max(1000f, worldCamera.far * 0.9f);
        }
        create();
    }

    public void setFlickerEnabled(boolean enabled) {
        this.enableFlicker = enabled;
    }

    public void setFlickerSpeed(float hz) {
        this.flickerSpeed = hz;
    }

    public void setStarRadius(float radius) {
        this.starRadius = radius;
    }
}
