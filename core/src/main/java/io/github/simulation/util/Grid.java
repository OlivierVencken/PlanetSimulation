package io.github.simulation.util;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

public class Grid {

    public final ModelBuilder modelBuilder;
    private final ModelBatch modelBatch;
    private ModelInstance gridInstance;
    public final int gridSize;
    public final int boxSize;

    public Grid(int gridSize, int boxSize, ModelBuilder modelBuilder) {
        this.modelBuilder = modelBuilder;
        this.modelBatch = new ModelBatch();
        this.gridSize = gridSize;
        this.boxSize = boxSize;
    }

    public void create() {
        int numLines = gridSize / boxSize + 1;
        ModelBuilder mb = modelBuilder;
        mb.begin();
        Material gridMat = new Material(ColorAttribute.createDiffuse(0.7f, 0.7f, 0.7f, 0.01f));
        long attr = Usage.Position | Usage.ColorUnpacked;
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder builder = mb.part("grid", GL20.GL_LINES, attr, gridMat);
        for (int i = 0; i < numLines; i++) {
            float pos = -gridSize / 2f + i * boxSize;
            // vertical lines (parallel to z)
            builder.setColor(0.7f, 0.7f, 0.7f, 0.05f);
            builder.line(pos, 0f, -gridSize / 2f, pos, 0f, gridSize / 2f);
            // horizontal lines (parallel to x)
            builder.setColor(0.7f, 0.7f, 0.7f, 0.05f);
            builder.line(-gridSize / 2f, 0f, pos, gridSize / 2f, 0f, pos);
        }
        Model gridModel = mb.end();
        gridInstance = new ModelInstance(gridModel);
    }

    public void render(Camera camera, Environment environment) {
        if (gridInstance != null) {
            modelBatch.begin(camera);
            modelBatch.render(gridInstance, environment);
            modelBatch.end();
        }
    }

    public void dispose() {
        if (gridInstance != null) {
            gridInstance.model.dispose();
        }
    }

    public Model getModel() {
        if (gridInstance != null) {
            return gridInstance.model;
        }
        return null;
    }
}
