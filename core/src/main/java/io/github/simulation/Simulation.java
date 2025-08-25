package io.github.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import io.github.simulation.render.BodyRenderer;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import io.github.simulation.input.FirstPersonCameraController;
import io.github.simulation.input.SimulationInputProcessor;
import io.github.simulation.physics.Body;
import io.github.simulation.physics.PhysicsEngine;
import io.github.simulation.render.TrailRenderer;
import io.github.simulation.util.Grid;

/**
 * Simulation class: creates the scene, camera, physics, trails and
 * overlay.
 */
public class Simulation {
    private Environment environment;
    private PerspectiveCamera camera;
    private ModelBuilder modelBuilder;
    private BodyRenderer bodyRenderer;
    private TrailRenderer trailRenderer;
    private Grid grid;

    private final Array<Body> bodies = new Array<>();
    private final Array<Model> modelsToDispose = new Array<>();

    // controllers & engines
    private FirstPersonCameraController fpController;
    private SimulationInputProcessor simInputProcessor;
    private PhysicsEngine physics;

    // rendering helpers
    private Stage uiStage;
    private Label timescaleLabel;
    private Label pausedLabel;
    private Label fpsLabel;
    private Label gridLabel;

    // focused body info UI
    private Table focusedInfoTable;
    private Label focusedNameLabel;
    private Label focusedMassLabel;
    private Label focusedRadiusLabel;
    private Label focusedPositionLabel;
    private Label focusedSpeedLabel;

    // simulation parameters
    private double timeScale = 1.0;
    private int substeps = 16;
    private boolean paused = false;
    private boolean trailsEnabled = true;
    private boolean showGrid = true;

    // time accumulator for simulation-time sampling
    private float simTime = 0f;

    // physics constants
    public static final double G = 6.67430e-11;
    public static final double G_SCALE = 1e6;
    public static final double SOFTENING = 1e-2;

    public void create() {
        modelBuilder = new ModelBuilder();
        bodyRenderer = new BodyRenderer();
        trailRenderer = new TrailRenderer(4096); // initial vertex capacity

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new PointLight().set(1f, 1f, 1f, new Vector3(0f, 200f, 200f), 1f));

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 100f, 300f);
        camera.lookAt(0f, 0f, 0f);
        camera.near = 0.1f;
        camera.far = 100000f;
        camera.update();

        physics = new PhysicsEngine(bodies);
        fpController = new FirstPersonCameraController(this, camera);
        simInputProcessor = new SimulationInputProcessor(this);

        // UI (Scene2D)
        uiStage = new Stage(new ScreenViewport());
        BitmapFont font = new BitmapFont();
        LabelStyle style = new LabelStyle(font, Color.WHITE);

        // stats
        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        timescaleLabel = new Label("Time Scale: " + timeScale, style);
        pausedLabel = new Label("Paused: " + paused, style);
        fpsLabel = new Label("FPS: " + Gdx.graphics.getFramesPerSecond(), style);
        gridLabel = new Label("Grid: " + showGrid, style);
        table.add(fpsLabel).left().pad(6).row();
        table.add(timescaleLabel).left().pad(6).row();
        table.add(pausedLabel).left().pad(6).row();
        table.add(gridLabel).left().pad(6).row();
        uiStage.addActor(table);

        // focused body info
        focusedInfoTable = new Table();
        focusedInfoTable.top().right();
        focusedInfoTable.setFillParent(true);
        focusedNameLabel = new Label("", style);
        focusedMassLabel = new Label("", style);
        focusedRadiusLabel = new Label("", style);
        focusedPositionLabel = new Label("", style);
        focusedSpeedLabel = new Label("", style);
        focusedInfoTable.add(focusedNameLabel).right().pad(6).row();
        focusedInfoTable.add(focusedMassLabel).right().pad(6).row();
        focusedInfoTable.add(focusedRadiusLabel).right().pad(6).row();
        focusedInfoTable.add(focusedPositionLabel).right().pad(6).row();
        focusedInfoTable.add(focusedSpeedLabel).right().pad(6).row();
        uiStage.addActor(focusedInfoTable);

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(fpController);
        multiplexer.addProcessor(simInputProcessor);
        Gdx.input.setInputProcessor(multiplexer);

        Gdx.input.setCursorCatched(true);

        buildScene();
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        simTime += delta * (float) timeScale;

        // update camera and UI
        fpController.update(delta);

        // physics update
        double simDt = delta * timeScale;
        if (!paused) {
            double subDt = simDt / Math.max(1, substeps);
            for (int i = 0; i < substeps; i++)
                physics.integrate(subDt);
        }

        // render bodies
        bodyRenderer.render(camera, environment, bodies);

        // render trails
        if (trailsEnabled) {
            trailRenderer.render(camera, bodies, (float) simTime);
        }

        // render grid for debugging
        if (showGrid) {
            grid.render(camera, environment);
        }

        // update UI labels
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());
        timescaleLabel.setText(String.format("timeScale: %.3g", timeScale));
        pausedLabel.setText("paused: " + paused);
        gridLabel.setText("Grid: " + showGrid);

        // update focused body info
        int idx = fpController.getFocusedBodyIndex();
        if (idx >= 0 && idx < bodies.size) {
            Body fb = bodies.get(idx);
            focusedNameLabel.setText("Name: " + fb.name);
            focusedMassLabel.setText(String.format("Mass: %.6g", fb.mass));
            focusedRadiusLabel.setText(String.format("Radius: %.3g", fb.radius));
            focusedPositionLabel.setText(String.format("Pos: (%.3g, %.3g, %.3g)", fb.pos[0], fb.pos[1], fb.pos[2]));
            double speed = Math.sqrt(fb.vel[0] * fb.vel[0] + fb.vel[1] * fb.vel[1] + fb.vel[2] * fb.vel[2]);
            focusedSpeedLabel.setText(String.format("Speed: %.3g", speed));
        } else {
            focusedNameLabel.setText("");
            focusedMassLabel.setText("");
            focusedRadiusLabel.setText("");
            focusedPositionLabel.setText("");
            focusedSpeedLabel.setText("");
        }

        // draw UI
        uiStage.act(delta);
        uiStage.draw();
    }

    public void dispose() {
        for (Model m : modelsToDispose) {
            m.dispose();
        } 
        if (bodyRenderer != null) {
            bodyRenderer.dispose();
        }
        if (trailRenderer != null) {
            trailRenderer.dispose();
        }
        uiStage.dispose();
    }

    // scene building
    private void buildScene() {
        for (Model m : modelsToDispose)
            m.dispose();
        modelsToDispose.clear();
        bodies.clear();

        grid = new Grid(2000, 50, modelBuilder);
        grid.create();
        modelsToDispose.add(grid.getModel());

        addBody("Sun", 333000, 0, 0, 0, 0, 0, 0, 20f, 1f, 0.9f, 0.2f);
        addOrbitingBody("Mercury", 100, 100, 0, 0, 0, 0, 0, 0.2f, 0.2f, 0.2f, 0.2f);
        addOrbitingBody("Venus", 100, 150, 0, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f, 0.4f);
        addOrbitingBody("Earth", 100, 200, 0, 0, 0, 0, 0, 0.5f, 0.2f, 0.6f, 1f);
        addOrbitingBody("Mars", 100, 250, 0, 0, 0, 0, 0, 0.3f, 0.95f, 0.35f, 0.25f);
        addOrbitingBody("Jupiter", 100, 300, 0, 0, 0, 0, 0, 1f, 0.5f, 0.2f, 0.1f);
        addOrbitingBody("Saturn", 100, 350, 0, 0, 0, 0, 0, 0.8f, 0.6f, 0.4f, 0.2f);
        addOrbitingBody("Uranus", 100, 400, 0, 0, 0, 0, 0, 0.5f, 0.7f, 0.8f, 0.3f);
        addOrbitingBody("Neptune", 100, 450, 0, 0, 0, 0, 0, 0.4f, 0.5f, 0.6f, 0.2f);

        Body Earth = bodies.get(3);
        addBodyWithRelativeVelocity("Moon", 1, Earth, 0.1, 1, 0, 0.12f, 0.8f, 0.8f, 0.8f);

        // stable 3 body simulation
        // set G = 1, G_scale = 1 and SOFTENING = 0
        // addBody("A", 1, -0.97000436, 0.24308753, 0, 0.4662036850, 0.4323657300,0,
        // 0.2f, 1f, 0f, 0f);
        // addBody("B", 1, 0.0,0.0, 0, -0.93240737, -0.86473146,0, 0.2f, 0f, 1f, 0f);
        // addBody("C", 1, 0.97000436,-0.24308753, 0, 0.4662036850, 0.4323657300,0,
        // 0.2f, 0f, 0f, 1f);
    }

    private void addBody(String name, double mass, double x, double y, double z,
            double vx, double vy, double vz,
            float radius, float r, float g, float b) {
        Material mat = new Material();
        mat.set(ColorAttribute.createDiffuse(r, g, b, 1f));
        Model model = modelBuilder.createSphere(radius * 2f, radius * 2f, radius * 2f, 24, 24, mat,
                Usage.Position | Usage.Normal);
        modelsToDispose.add(model);
        ModelInstance instance = new ModelInstance(model);
        Body body = new Body(name, mass, x, y, z, vx, vy, vz, radius, instance, r, g, b);
        bodies.add(body);
    }

    private void addOrbitingBody(String name, double mass, double x, double y, double z, double vx, double vy,
            double vz, float radius, float r, float g, float b) {
        double cx = bodies.size > 0 ? bodies.get(0).pos[0] : 0.0;
        double cy = bodies.size > 0 ? bodies.get(0).pos[1] : 0.0;
        double cz = bodies.size > 0 ? bodies.get(0).pos[2] : 0.0;
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        double rdist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double centralMass = bodies.size > 0 ? bodies.get(0).mass : 0.0;
        double v_circ = Math.sqrt((G * G_SCALE) * centralMass / Math.max(rdist, 1e-6));
        double vxp = -dz;
        double vyp = 0;
        double vzp = dx;
        double len = Math.sqrt(vxp * vxp + vyp * vyp + vzp * vzp);
        if (len == 0) {
            vxp = 0;
            vyp = 1;
            vzp = 0;
            len = 1;
        }
        vxp = vxp / len * v_circ;
        vyp = vyp / len * v_circ;
        vzp = vzp / len * v_circ;
        addBody(name, mass, x, y, z, vx + vxp, vy + vyp, vz + vzp, radius, r, g, b);
    }

    private void addBodyWithRelativeVelocity(String name, double mass, Body parent, double relX, double relY,
            double relZ, float radius, float r, float g, float b) {
        double x = parent.pos[0] + relX;
        double y = parent.pos[1] + relY;
        double z = parent.pos[2] + relZ;
        double dx = x - parent.pos[0];
        double dy = y - parent.pos[1];
        double dz = z - parent.pos[2];
        double rdist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v_circ = Math.sqrt((G * G_SCALE) * parent.mass / Math.max(rdist, 1e-6));
        double vxp = -dz, vyp = 0, vzp = dx;
        double len = Math.sqrt(vxp * vxp + vyp * vyp + vzp * vzp);
        vxp = vxp / len * v_circ;
        vyp = vyp / len * v_circ;
        vzp = vzp / len * v_circ;
        addBody(name, mass, x, y, z, parent.vel[0] + vxp, parent.vel[1] + vyp, parent.vel[2] + vzp, radius, r, g, b);
    }

    // getters / setters / control
    public Array<Body> getBodies() {
        return bodies;
    }

    public FirstPersonCameraController getFpsController() {
        return fpController;
    }

    public void setPaused(boolean p) {
        paused = p;
    }

    public boolean isPaused() {
        return paused;
    }

    public void reset() {
        buildScene();
    }

    public boolean isTrailsEnabled() {
        return trailsEnabled;
    }

    public void setTrailsEnabled(boolean enabled) {
        this.trailsEnabled = enabled;
    }

    public boolean isGridShown() {
        return showGrid;
    }

    public void setGridShown(boolean shown) {
        this.showGrid = shown;
    }

    public boolean isFocusedOnBody() {
        return fpController != null && fpController.isFocusedOnBody();
    }

    public void increaseTimeScale() {
        timeScale *= 2.0;
    }

    public void decreaseTimeScale() {
        timeScale = Math.max(1e-9, timeScale / 2.0);
    }

    public void singleStep() {
        if (paused)
            physics.integrate(1.0 / 60.0);
    }
}
