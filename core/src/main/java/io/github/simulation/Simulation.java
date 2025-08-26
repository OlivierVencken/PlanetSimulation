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
import io.github.simulation.util.OrbitUtils;

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
    private Label fpsLabel;
    private Label timescaleLabel;
    private Label elapsedTimeLabel;
    private Label pausedLabel;

    // focused body info UI
    private Table focusedInfoTable;
    private Label focusedNameLabel;
    private Label focusedMassLabel;
    private Label focusedRadiusLabel;
    private Label focusedSpeedLabel;

    private boolean paused = false;
    private boolean trailsEnabled = true;
    private boolean showGrid = false;

    // simulation parameters
    private double timeScale = 86400.0; // number of simulation seconds simulated per 1 real second
    private int substeps = 512;
    private float simTime = 0f;

    // physics constants
    public static final double G = 6.67430e-11; // SI
    public static final double LENGTH_SCALE = 1e8; // 1 sim unit = 100,000 km
    public static final double MASS_SCALE = 1e21;
    // gravitational constant adjusted to simulation units: G_SIM = G * MASS_SCALE /
    // Ls^3
    public static final double G_SIM = G * MASS_SCALE / (LENGTH_SCALE * LENGTH_SCALE * LENGTH_SCALE);
    public static final double SOFTENING = 1e-3;

    public void create() {
        modelBuilder = new ModelBuilder();
        bodyRenderer = new BodyRenderer();
        trailRenderer = new TrailRenderer(4096); // initial vertex capacity

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new PointLight().set(1f, 1f, 1f, new Vector3(0f, 200f, 200f), 1f));

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 10f, 20f);
        camera.lookAt(0f, 0f, 0f);
        camera.near = 0.01f;
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
        fpsLabel = new Label("FPS: " + Gdx.graphics.getFramesPerSecond(), style);
        timescaleLabel = new Label("Time Scale: " + timeScale, style);
        elapsedTimeLabel = new Label("Elapsed: 0s", style);
        pausedLabel = new Label("Paused: " + paused, style);
        table.add(fpsLabel).left().pad(6).row();
        table.add(timescaleLabel).left().pad(6).row();
        table.add(elapsedTimeLabel).left().pad(6).row();
        table.add(pausedLabel).left().pad(6).row();
        uiStage.addActor(table);

        // focused body info
        focusedInfoTable = new Table();
        focusedInfoTable.top().right();
        focusedInfoTable.setFillParent(true);
        focusedNameLabel = new Label("", style);
        focusedMassLabel = new Label("", style);
        focusedRadiusLabel = new Label("", style);
        focusedSpeedLabel = new Label("", style);
        focusedInfoTable.add(focusedNameLabel).right().pad(6).row();
        focusedInfoTable.add(focusedMassLabel).right().pad(6).row();
        focusedInfoTable.add(focusedRadiusLabel).right().pad(6).row();
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

        if (!paused) {
            simTime += delta * (float) timeScale;

            // physics update
            double simDt = delta * timeScale;

            double subDt = simDt / Math.max(1, substeps);
            for (int i = 0; i < substeps; i++) {
                physics.integrate(subDt);
            }
        }

        // update camera
        fpController.update(delta);

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
        timescaleLabel.setText(String.format("timeScale: %.6g", timeScale));
        pausedLabel.setText("paused: " + paused);
        elapsedTimeLabel.setText("Elapsed: " + formatElapsedTime(simTime));

        // update focused body info
        int idx = fpController.getFocusedBodyIndex();
        if (idx >= 0 && idx < bodies.size) {
            Body fb = bodies.get(idx);
            focusedNameLabel.setText("Name: " + fb.name);
            focusedMassLabel.setText(String.format("Mass: %.4g", fb.mass * MASS_SCALE) + " m");
            focusedRadiusLabel.setText(String.format("Radius: %.4g", fb.radius * LENGTH_SCALE) + " m");
            double speed = Math.sqrt(fb.vel[0] * fb.vel[0] + fb.vel[1] * fb.vel[1] + fb.vel[2] * fb.vel[2])
                    * LENGTH_SCALE;
            focusedSpeedLabel.setText(String.format("Speed: %.6g", speed) + " m/s");
        } else {
            focusedNameLabel.setText("");
            focusedMassLabel.setText("");
            focusedRadiusLabel.setText("");
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

        grid = new Grid(10000, 5, modelBuilder);
        grid.create();
        modelsToDispose.add(grid.getModel());

        // all units are in SI (meters, kg, seconds)
        addBody("Sun", 1.989e30, 0, 0, 0, 0, 0, 0, 696340e3f, 1f, 0.9f, 0.2f);

        addOrbitingBody("Mercury", 3.3011e23, 57.91e9, 0, 0, 0, 0, 0, 2439.7e3f, 0.2f, 0.2f, 0.2f);
        addOrbitingBody("Venus", 4.8675e24, 108.2e9, 0, 0, 0, 0, 0, 6051.8e3f, 0.4f, 0.4f, 0.4f);
        addOrbitingBody("Earth", 5.9722e24, 149.6e9, 0, 0, 0, 0, 0, 6371e3f, 0.2f, 0.6f, 1f);
        addOrbitingBody("Mars", 6.4171e23, 227.9e9, 0, 0, 0, 0, 0, 3389.5e3f, 0.95f, 0.35f, 0.25f);
        addOrbitingBody("Jupiter", 1.8982e27, 778.5e9, 0, 0, 0, 0, 0, 69911e3f, 0.5f, 0.2f, 0.1f);
        addOrbitingBody("Saturn", 5.6834e26, 1434e9, 0, 0, 0, 0, 0, 58232e3f, 0.6f, 0.4f, 0.2f);
        addOrbitingBody("Uranus", 8.6810e25, 2871e9, 0, 0, 0, 0, 0, 25362e3f, 0.7f, 0.8f, 0.3f);
        addOrbitingBody("Neptune", 1.02413e26, 4495e9, 0, 0, 0, 0, 0, 24622e3f, 0.5f, 0.6f, 0.2f);

        Body earth = bodies.get(3);
        addBodyWithRelativeVelocity("Moon", 7.342e22, earth, 0, 384400e3, 0, 1737.4e3f, 0.8f, 0.8f, 0.8f);
    }

    private void addBody(String name, double mass, double x, double y, double z,
            double vx, double vy, double vz,
            float radius, float r, float g, float b) {
        // convert real-world units (meters, kg, m/s) to simulation units
        double simMass = mass / MASS_SCALE;
        double simX = x / LENGTH_SCALE;
        double simY = y / LENGTH_SCALE;
        double simZ = z / LENGTH_SCALE;
        double simVx = vx / LENGTH_SCALE;
        double simVy = vy / LENGTH_SCALE;
        double simVz = vz / LENGTH_SCALE;
        float simRadius = (float) (radius / LENGTH_SCALE);

        Material mat = new Material();
        mat.set(ColorAttribute.createDiffuse(r, g, b, 1f));
        Model model = modelBuilder.createSphere(simRadius * 2f, simRadius * 2f, simRadius * 2f, 24, 24, mat,
                Usage.Position | Usage.Normal);
        modelsToDispose.add(model);
        ModelInstance instance = new ModelInstance(model);
        Body body = new Body(name, simMass, simX, simY, simZ, simVx, simVy, simVz, simRadius, instance, r, g, b);

        bodies.add(body);
    }

    private void addOrbitingBody(String name, double mass, double x, double y, double z, double vx, double vy,
            double vz, float radius, float r, float g, float b) {
        double simX = x / LENGTH_SCALE;
        double simY = y / LENGTH_SCALE;
        double simZ = z / LENGTH_SCALE;
        double simVx = vx / LENGTH_SCALE;
        double simVy = vy / LENGTH_SCALE;
        double simVz = vz / LENGTH_SCALE;

        double cx = bodies.size > 0 ? bodies.get(0).pos[0] : 0.0;
        double cy = bodies.size > 0 ? bodies.get(0).pos[1] : 0.0;
        double cz = bodies.size > 0 ? bodies.get(0).pos[2] : 0.0;
        double centralMass = bodies.size > 0 ? bodies.get(0).mass : 0.0;

        double[] tangential = OrbitUtils.computeCircularVelocity(simX, simY, simZ, cx, cy, cz, centralMass,
                Simulation.G_SIM);

        double totalVx_mps = (simVx + tangential[0]) * Simulation.LENGTH_SCALE;
        double totalVy_mps = (simVy + tangential[1]) * Simulation.LENGTH_SCALE;
        double totalVz_mps = (simVz + tangential[2]) * Simulation.LENGTH_SCALE;

        addBody(name, mass, x, y, z, totalVx_mps, totalVy_mps, totalVz_mps, radius, r, g, b);
    }

    private void addBodyWithRelativeVelocity(String name, double mass, Body parent, double relX, double relY,
            double relZ, float radius, float r, float g, float b) {
        double simRelX = relX / LENGTH_SCALE;
        double simRelY = relY / LENGTH_SCALE;
        double simRelZ = relZ / LENGTH_SCALE;
        double x = parent.pos[0] + simRelX;
        double y = parent.pos[1] + simRelY;
        double z = parent.pos[2] + simRelZ;

        double[] tangential = OrbitUtils.computeCircularVelocity(x, y, z, parent.pos[0], parent.pos[1], parent.pos[2],
                parent.mass, Simulation.G_SIM);

        addBody(name, mass,
                x * Simulation.LENGTH_SCALE, y * Simulation.LENGTH_SCALE, z * Simulation.LENGTH_SCALE,
                (parent.vel[0] + tangential[0]) * Simulation.LENGTH_SCALE,
                (parent.vel[1] + tangential[1]) * Simulation.LENGTH_SCALE,
                (parent.vel[2] + tangential[2]) * Simulation.LENGTH_SCALE,
                radius, r, g, b);
    }

    private String formatElapsedTime(float seconds) {
        if (seconds < 60) {
            return String.format("%.1fs", seconds);
        } else if (seconds < 3600) {
            return String.format("%.1fm", seconds / 60);
        } else if (seconds < 86400) {
            return String.format("%.1fh", seconds / 3600);
        } else if (seconds < 86400 * 30.44) { // average days per month
            return String.format("%.1fd", seconds / 86400);
        } else if (seconds < 86400 * 365.25) { // days per year
            return String.format("%.1fmo", seconds / (86400 * 30.44));
        } else {
            return String.format("%.2fy", seconds / (86400 * 365.25));
        }
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
        if (paused) {
            physics.integrate(1.0 / 60.0);
        }
    }
}
