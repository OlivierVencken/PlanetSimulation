package io.github.simulation.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.Array;
import io.github.simulation.physics.Body;

/**
 * Body renderer for rendering all ModelInstances of bodies.
 * Handles batch rendering and camera/environment setup.
 */
public class BodyRenderer {
    private final ModelBatch modelBatch;
    private final SpriteBatch spriteBatch;
    private Texture glowTexture;
    private TextureRegion glowRegion;
    private final Matrix4 orthoMatrix = new Matrix4();
    private final Vector3 tmpProj = new Vector3();
    private final Vector3 tmpProjOffset = new Vector3();
    private final Vector3 cameraRight = new Vector3();

    public BodyRenderer() {
        this.modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        glowTexture = createGlowTexture(128);
        glowTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        glowRegion = new TextureRegion(glowTexture);
    }

    public ModelBatch getModelBatch() {
        return modelBatch;
    }

    private Texture createGlowTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Format.RGBA8888);
        float cx = size * 0.5f;
        float cy = size * 0.5f;
        float maxDist = (float) Math.sqrt(cx * cx + cy * cy);

        float innerRadiusFraction = 0.12f;
        float innerRadius = Math.min(maxDist, size * innerRadiusFraction);
        float falloffExp = 16f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);

                float alpha;
                if (d <= innerRadius) {
                    // fully opaque core
                    alpha = 1f;
                } else if (d >= maxDist) {
                    alpha = 0f;
                } else {
                    // map distance from innerRadius..maxDist -> 1..0 then pow for sharp falloff
                    float t = 1f - ((d - innerRadius) / (maxDist - innerRadius));
                    t = MathUtils.clamp(t, 0f, 1f);
                    alpha = (float) Math.pow(t, falloffExp);
                }

                // white color, variable alpha
                pm.setColor(1f, 1f, 1f, alpha);
                pm.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        tex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return tex;
    }

    public void render(PerspectiveCamera cam, Environment environment, Array<Body> bodies) {
        // Enable for 3d rendering without glow
        //modelBatch.begin(cam);
        //for (Body b : bodies) {
        //    b.instance.transform.setToTranslation((float) b.pos[0], (float) b.pos[1],
        //            (float) b.pos[2]);
        //    modelBatch.render(b.instance, environment);
        //}
        //modelBatch.end();

        // Render glow effect for each body
        orthoMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        spriteBatch.setProjectionMatrix(orthoMatrix);
        spriteBatch.begin();

        spriteBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        cameraRight.set(cam.direction).crs(cam.up).nor();

        for (Body b : bodies) {
            cam.update();

            tmpProj.set((float) b.pos[0], (float) b.pos[1], (float) b.pos[2]);
            tmpProj.sub(cam.position); // tmpProj now = bodyPos - cam.position

            // Cull bodies behind the camera
            if (cam.direction.dot(tmpProj) <= 0f) {
                continue;
            }

            tmpProj.set((float) b.pos[0], (float) b.pos[1], (float) b.pos[2]);
            cam.project(tmpProj);

            // if projected depth or coords invalid --> skip
            if (!Float.isFinite(tmpProj.x) || !Float.isFinite(tmpProj.y)) {
                continue;
            }

            tmpProjOffset.set((float) b.pos[0], (float) b.pos[1], (float) b.pos[2]);
            tmpProjOffset.mulAdd(cameraRight, b.radius * 3f);
            cam.project(tmpProjOffset);

            float pixelRadius = Math.abs(tmpProjOffset.x - tmpProj.x);
            if (!Float.isFinite(pixelRadius)) {
                continue;
            }

            float minPixelRadius = 1.5f;
            if (pixelRadius < minPixelRadius) {
                pixelRadius = minPixelRadius;
            }

            float dist = cam.position.dst((float) b.pos[0], (float) b.pos[1], (float) b.pos[2]);

            // weaker attenuation for core (so core remains bright)
            float distAtten = 1f / (1f + dist * 0.003f);
            distAtten = Math.max(0.12f, Math.min(1f, distAtten));

            float outerScale = 1.4f;
            float midScale = 1.25f;
            float coreScale = 1f;

            float outerSize = pixelRadius * 2f * outerScale;
            float midSize = pixelRadius * 2f * midScale;
            float coreSize = pixelRadius * 2f * coreScale;

            float outerAlpha = MathUtils.clamp(0.18f * distAtten, 0.02f, 0.7f);
            float midAlpha = MathUtils.clamp(0.45f * distAtten, 0.04f, 0.95f);
            float coreAlpha = MathUtils.clamp(1.0f * (0.85f + 0.15f * distAtten), 0.6f, 1.0f);

            // Outer halo
            spriteBatch.setColor(b.cr, b.cg, b.cb, outerAlpha);
            spriteBatch.draw(glowRegion,
                    tmpProj.x - outerSize * 0.5f,
                    tmpProj.y - outerSize * 0.5f,
                    outerSize, outerSize);

            // Mid halo
            spriteBatch.setColor(b.cr, b.cg, b.cb, midAlpha);
            spriteBatch.draw(glowRegion,
                    tmpProj.x - midSize * 0.5f,
                    tmpProj.y - midSize * 0.5f,
                    midSize, midSize);

            // Core
            spriteBatch.flush();
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
            spriteBatch.setColor(b.cr, b.cg, b.cb, coreAlpha);
            spriteBatch.draw(glowRegion,
                    tmpProj.x - coreSize * 0.5f,
                    tmpProj.y - coreSize * 0.5f,
                    coreSize, coreSize);

            // draw second time with reduced size to concentrate brightness
            float coreInnerSize = coreSize * 0.6f;
            spriteBatch.draw(glowRegion,
                    tmpProj.x - coreInnerSize * 0.5f,
                    tmpProj.y - coreInnerSize * 0.5f,
                    coreInnerSize, coreInnerSize);

            spriteBatch.flush();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        }

        spriteBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        spriteBatch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        spriteBatch.end();
    }

    public void dispose() {
        modelBatch.dispose();
        spriteBatch.dispose();
        glowRegion.getTexture().dispose();
    }
}
