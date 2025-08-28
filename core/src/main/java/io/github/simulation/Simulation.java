package io.github.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import io.github.simulation.input.FirstPersonCameraController;
import io.github.simulation.input.SimulationInputProcessor;
import io.github.simulation.physics.Body;
import io.github.simulation.physics.PhysicsEngine;
import io.github.simulation.render.BodyRenderer;
import io.github.simulation.render.StarfieldRenderer;
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
    private StarfieldRenderer starfield;
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
    private Label fovLabel;

    // focused body info UI
    private Table focusedInfoTable;
    private Label focusedNameLabel;
    private Label focusedMassLabel;
    private Label focusedRadiusLabel;
    private Label focusedSpeedLabel;

    private boolean paused = false;
    private boolean trailsEnabled = false;
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
    public static final double SOFTENING = 0; //1e-3

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
        camera.far = 10000f;
        camera.update();

        starfield = new StarfieldRenderer(10000);
        starfield.create(camera);

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
        fovLabel = new Label("FOV: " + (int) camera.fieldOfView, style);
        table.add(fpsLabel).left().pad(6).row();
        table.add(timescaleLabel).left().pad(6).row();
        table.add(elapsedTimeLabel).left().pad(6).row();
        table.add(pausedLabel).left().pad(6).row();
        table.add(fovLabel).left().pad(6).row();
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

        // clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);

        // render starfield
        starfield.render(camera);

        // render bodies
        bodyRenderer.render(camera, environment, bodies);

        // render trails
        if (trailsEnabled) {
            trailRenderer.render(camera, bodies, simTime);
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
        fovLabel.setText("FOV: " + (int) camera.fieldOfView);

        // update focused body info
        int idx = fpController.getFocusedBodyIndex();
        if (idx >= 0 && idx < bodies.size) {
            Body fb = bodies.get(idx);
            focusedNameLabel.setText("Name: " + fb.name);
            focusedMassLabel.setText(String.format("Mass: %.4g", fb.mass * MASS_SCALE) + " kg");
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
        if (starfield != null) {
            starfield.dispose();
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

        // --- dwarf planets ---
        addOrbitingBody("Pluto", 1.303e22, 5.906376e12, 0, 0, 0, 0, 0, 1188.3e3f, 0.8f, 0.6f, 0.5f);
        addOrbitingBody("Ceres", 9.393e20, 414.01e9, 0, 0, 0, 0, 0, 473.0e3f, 0.6f, 0.6f, 0.6f);

        Body earth = bodies.get(3);
        Body mars = bodies.get(4);
        Body jupiter = bodies.get(5);
        Body saturn = bodies.get(6);
        Body uranus = bodies.get(7);
        Body neptune = bodies.get(8);
        Body pluto = bodies.get(9);

        // --- Earth ---
        addBodyWithRelativeVelocity("Moon", 7.342000e+22, earth,
                3.683615e+08, 2.019048e+07, 1.817938e+06,
                1737400.0f, 0.8f, 0.8f, 0.8f);

        // --- Mars ---
        addBodyWithRelativeVelocity("Phobos", 1.065900e+16, mars,
                9.095125e+06, 1.603433e+06, 3.010441e+04,
                11270.0f, 0.5f, 0.5f, 0.5f);

        addBodyWithRelativeVelocity("Deimos", 1.476200e+15, mars,
                -1.173041e+07, 2.030777e+07, 6.338690e+05,
                6200.0f, 0.6f, 0.6f, 0.6f);

        // --- Jupiter ---
        addBodyWithRelativeVelocity("Io", 8.930000e+22, jupiter,
                -1.717852e+08, 3.835097e+08, 3.346754e+05,
                1821600.0f, 0.95f, 0.75f, 0.35f);

        addBodyWithRelativeVelocity("Europa", 4.800000e+22, jupiter,
                -6.576218e+08, 1.281818e+08, 1.051505e+06,
                1560800.0f, 0.9f, 0.9f, 1.0f);

        addBodyWithRelativeVelocity("Ganymede", 1.481900e+23, jupiter,
                9.496836e+08, -4.961215e+08, -1.532640e+06,
                2631200.0f, 0.7f, 0.7f, 0.8f);

        addBodyWithRelativeVelocity("Callisto", 1.075900e+23, jupiter,
                1.276965e+09, -1.386591e+09, -4.646530e+06,
                2410300.0f, 0.55f, 0.55f, 0.55f);

        // --- Saturn ---
        addBodyWithRelativeVelocity("Titan", 1.345200e+23, saturn,
                -8.662181e+08, -8.224852e+08, -4.737227e+06,
                2574700.0f, 1.0f, 0.6f, 0.3f);

        addBodyWithRelativeVelocity("Rhea", 2.306500e+21, saturn,
                -5.068090e+08, 1.448344e+08, 8.721146e+05,
                764300.0f, 0.75f, 0.75f, 0.75f);

        addBodyWithRelativeVelocity("Iapetus", 1.805600e+21, saturn,
                2.344478e+09, 2.770206e+09, 3.641635e+08,
                734500.0f, 0.82f, 0.72f, 0.6f);

        addBodyWithRelativeVelocity("Dione", 1.095500e+21, saturn,
                3.492484e+08, 1.411055e+08, 4.679236e+04,
                561400.0f, 0.78f, 0.78f, 0.78f);

        addBodyWithRelativeVelocity("Tethys", 6.175000e+20, saturn,
                1.473438e+08, -2.551608e+08, -4.859242e+06,
                531100.0f, 0.8f, 0.8f, 0.8f);

        addBodyWithRelativeVelocity("Enceladus", 1.080200e+20, saturn,
                2.257664e+08, 7.335596e+07, 1.152273e+04,
                252100.0f, 0.95f, 0.95f, 1.0f);

        addBodyWithRelativeVelocity("Mimas", 3.749300e+19, saturn,
                -1.352228e+08, -1.217093e+08, -3.340126e+06,
                198200.0f, 0.7f, 0.7f, 0.8f);

        // --- Uranus ---
        addBodyWithRelativeVelocity("Titania", 3.527000e+21, uranus,
                -3.782374e+08, -2.183753e+08, -3.010982e+05,
                788900.0f, 0.7f, 0.8f, 0.9f);

        addBodyWithRelativeVelocity("Oberon", 3.014000e+21, uranus,
                0.000000e+00, 5.829938e+08, 6.919113e+05,
                761400.0f, 0.6f, 0.7f, 0.8f);

        addBodyWithRelativeVelocity("Umbriel", 1.172000e+21, uranus,
                -1.043081e-07, -2.654776e+08, -5.930837e+05,
                584700.0f, 0.55f, 0.6f, 0.7f);

        addBodyWithRelativeVelocity("Ariel", 1.353000e+21, uranus,
                -2.980232e-08, -1.911935e+08, -8.676146e+05,
                578900.0f, 0.7f, 0.7f, 0.8f);

        addBodyWithRelativeVelocity("Miranda", 6.590000e+19, uranus,
                1.059543e+08, -7.418751e+07, -6.033977e+05,
                235800.0f, 0.6f, 0.6f, 0.7f);

        // --- Neptune ---
        addBodyWithRelativeVelocity("Triton", 2.140000e+22, neptune,
                -1.983763e+08, 2.704535e+08, -1.155535e+08,
                1353400.0f, 0.6f, 0.7f, 0.95f);

        // --- Pluto ---
        addBodyWithRelativeVelocity("Charon", 1.586000e+21, pluto,
                -1.960392e+07, 2.400788e-09, 0.000000e+00,
                606000.0f, 0.7f, 0.6f, 0.6f);
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
