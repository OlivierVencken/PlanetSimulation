package io.github.simulation.render;

import com.badlogic.gdx.math.Vector3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.utils.Array;
import io.github.simulation.physics.Body;
import io.github.simulation.physics.Body.TrailPoint;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Trail renderer using a dynamic Mesh with Position + ColorPacked
 * per-vertex.
 */
public class TrailRenderer {
    private Mesh mesh;
    private final int floatsPerVertex = 4;
    private int capacityVertices;
    private ShaderProgram shader;
    private final float trailSampleInterval = 0.01f;
    private final float trailDuration = 64f;

    public TrailRenderer(int initialCapacityVertices) {
        this.capacityVertices = initialCapacityVertices;
        createMesh(capacityVertices);
        createShader();
    }

    private void createMesh(int maxVertices) {
        if (mesh != null) {
            mesh.dispose();
        }
        // Position (3 floats) + ColorPacked (1 float)
        mesh = new Mesh(true, maxVertices, 0,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.ColorPacked, 4, "a_color"));
        capacityVertices = maxVertices;
    }

    private void createShader() {
        // Minimal vertex and fragment shaders compatible with LibGDX vertex attributes
        String vertexShader = "attribute vec3 a_position;\n" +
                "attribute vec4 a_color;\n" +
                "uniform mat4 u_projViewTrans;\n" +
                "varying vec4 v_color;\n" +
                "void main() {\n" +
                "    v_color = a_color;\n" +
                "    gl_Position = u_projViewTrans * vec4(a_position, 1.0);\n" +
                "}\n";

        String fragmentShader = "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "varying vec4 v_color;\n" +
                "void main() {\n" +
                "    gl_FragColor = v_color;\n" +
                "}\n";

        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            Gdx.app.error("TrailRenderer", "Shader compile error: " + shader.getLog());
            shader = null;
        }
    }

    public void render(PerspectiveCamera cam, Array<Body> bodies, float simTime) {
        for (Body b : bodies) {
            TrailRenderer.maybeSampleTrail(b, simTime, trailSampleInterval, trailDuration);
        }

        if (shader == null) {
            return;
        }

        // Count segments first
        int segmentCount = 0;
        for (Body b : bodies) {
            Array<TrailPoint> t = b.getTrail();
            if (t.size > 1) {
                segmentCount += (t.size - 1);
            }
        }

        int neededVertices = segmentCount * 2;
        if (neededVertices == 0) {
            return;
        }

        // grow mesh if necessary
        if (neededVertices > capacityVertices) {
            int newCap = Math.max(neededVertices, (int) (capacityVertices * 1.5f));
            createMesh(newCap);
        }

        // Build vertex buffer: x,y,z, packedColor (float) per vertex
        float[] verts = new float[neededVertices * floatsPerVertex];
        int vi = 0;

        float cutoff = simTime - trailDuration;

        for (Body b : bodies) {
            Array<TrailPoint> t = b.getTrail();
            if (t.size < 2)
                continue;

            for (int i = 0; i < t.size - 1; i++) {
                TrailPoint a = t.get(i);
                TrailPoint c = t.get(i + 1);

                // skip segments entirely before cutoff
                if (c.simTime <= cutoff)
                    continue;

                // compute alpha for endpoints (0..1)
                float ageA = a.simTime - cutoff;
                float ageC = c.simTime - cutoff;
                float alphaA = Math.max(0f, Math.min(1f, ageA / trailDuration));
                float alphaC = Math.max(0f, Math.min(1f, ageC / trailDuration));

                // pack color as float (Color.toFloatBits returns float)
                float packedA = Color.toFloatBits(b.cr, b.cg, b.cb, alphaA);
                float packedC = Color.toFloatBits(b.cr, b.cg, b.cb, alphaC);

                // vertex A
                verts[vi++] = a.p.x;
                verts[vi++] = a.p.y;
                verts[vi++] = a.p.z;
                verts[vi++] = packedA;

                // vertex C
                verts[vi++] = c.p.x;
                verts[vi++] = c.p.y;
                verts[vi++] = c.p.z;
                verts[vi++] = packedC;
            }
        }

        if (vi == 0) {
            return; // nothing to upload
        }

        // upload vertices (vi floats)
        mesh.setVertices(verts, 0, vi);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // line width
        try {
            Gdx.gl.glLineWidth(2f);
        } catch (Throwable ignored) {
        }

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", cam.combined);
        mesh.render(shader, GL20.GL_LINES);
    }

    public static void maybeSampleTrail(Body body, float currentSimTime, float sampleInterval, float trailDuration) {
        if (currentSimTime - body.getLastSampleSimTime() < sampleInterval) {
            return;
        }
        body.setLastSampleSimTime(currentSimTime);
        body.getTrail().add(new Body.TrailPoint(
                new Vector3((float) body.pos[0], (float) body.pos[1], (float) body.pos[2]), currentSimTime));
        float cutoff = currentSimTime - trailDuration;
        while (body.getTrail().size > 0 && body.getTrail().get(0).simTime < cutoff) {
            body.getTrail().removeIndex(0);
        }
    }

    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }
}
