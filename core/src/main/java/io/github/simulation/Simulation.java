// File: Simulation.java
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
    public static final double SOFTENING = 0; // 1e-3

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
        addBodyFromKepler("Moon", 7.342000e22, earth,
                384400e3, 0.0549, 5.145, 125.08, 318.15, 45.0,
                1737400.0f, 0.8f, 0.8f, 0.8f);

        // --- Mars ---
        addBodyFromKepler("Phobos", 1.065900e16, mars,
                9376e3, 0.0151, 1.075, 0.0, 0.0, 10.0,
                11270.0f, 0.5f, 0.5f, 0.5f);

        addBodyFromKepler("Deimos", 1.476200e15, mars,
                23463e3, 0.0002, 1.793, 0.0, 0.0, 200.0,
                6200.0f, 0.6f, 0.6f, 0.6f);

        // --- Jupiter (Galilean moons) ---
        addBodyFromKepler("Io", 8.93e22, jupiter,
                421700e3, 0.0041, 0.036, 43.977, 84.129, 10.0,
                1821600.0f, 0.95f, 0.75f, 0.35f);

        addBodyFromKepler("Europa", 4.80e22, jupiter,
                671100e3, 0.009, 0.470, 219.106, 88.970, 80.0,
                1560800.0f, 0.9f, 0.9f, 1.0f);

        addBodyFromKepler("Ganymede", 1.4819e23, jupiter,
                1070400e3, 0.0013, 0.177, 63.552, 192.417, 140.0,
                2631200.0f, 0.7f, 0.7f, 0.8f);

        addBodyFromKepler("Callisto", 1.0759e23, jupiter,
                1882700e3, 0.0074, 0.192, 298.848, 52.643, 260.0,
                2410300.0f, 0.55f, 0.55f, 0.55f);

        // --- Saturn (major moons) ---
        addBodyFromKepler("Titan", 1.3452e23, saturn,
                1221870e3, 0.0288, 0.330, 168.811, 183.517, 40.0,
                2574700.0f, 1.0f, 0.6f, 0.3f);

        addBodyFromKepler("Rhea", 2.3065e21, saturn,
                527040e3, 0.0013, 0.345, 60.094, 69.051, 95.0,
                764300.0f, 0.75f, 0.75f, 0.75f);

        addBodyFromKepler("Iapetus", 1.8056e21, saturn,
                3560820e3, 0.0283, 7.489, 33.0, 200.0, 210.0,
                734500.0f, 0.82f, 0.72f, 0.6f);

        addBodyFromKepler("Dione", 1.0955e21, saturn,
                377396e3, 0.0022, 0.019, 192.0, 352.0, 30.0,
                561400.0f, 0.78f, 0.78f, 0.78f);

        addBodyFromKepler("Tethys", 6.175e20, saturn,
                294660e3, 0.0001, 1.091, 51.0, 140.0, 160.0,
                531100.0f, 0.8f, 0.8f, 0.8f);

        addBodyFromKepler("Enceladus", 1.0802e20, saturn,
                237948e3, 0.0047, 0.009, 8.0, 78.0, 300.0,
                252100.0f, 0.95f, 0.95f, 1.0f);

        addBodyFromKepler("Mimas", 3.7493e19, saturn,
                185520e3, 0.0196, 1.572, 83.0, 210.0, 12.0,
                198200.0f, 0.7f, 0.7f, 0.8f);

        // --- Uranus (regular large moons) ---
        addBodyFromKepler("Titania", 3.527e21, uranus,
                436300e3, 0.0011, 0.079, 120.0, 10.0, 200.0,
                788900.0f, 0.7f, 0.8f, 0.9f);

        addBodyFromKepler("Oberon", 3.014e21, uranus,
                583520e3, 0.0014, 0.068, 25.0, 40.0, 50.0,
                761400.0f, 0.6f, 0.7f, 0.8f);

        addBodyFromKepler("Umbriel", 1.172e21, uranus,
                266000e3, 0.0039, 0.128, 78.0, 330.0, 300.0,
                584700.0f, 0.55f, 0.6f, 0.7f);

        addBodyFromKepler("Ariel", 1.353e21, uranus,
                191020e3, 0.0012, 0.260, 200.0, 130.0, 140.0,
                578900.0f, 0.7f, 0.7f, 0.8f);

        addBodyFromKepler("Miranda", 6.59e19, uranus,
                129390e3, 0.0013, 0.466, 310.0, 250.0, 75.0,
                235800.0f, 0.6f, 0.6f, 0.7f);

        // --- Neptune ---
        addBodyFromKepler("Triton", 2.14e22, neptune,
                354760e3, 0.000016, 156.865, 98.0, 256.0, 340.0,
                1353400.0f, 0.6f, 0.7f, 0.95f);

        // --- Pluto / Charon ---
        addBodyFromKepler("Charon", 1.586e21, pluto,
                19600e3, 0.0002, 0.0, 0.0, 0.0, 180.0,
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

        // pass null orbitalNormal so OrbitUtils will numerically estimate an orbital
        // normal
        double[] tangential = OrbitUtils.computeCircularVelocity(simX, simY, simZ, cx, cy, cz, centralMass,
                Simulation.G_SIM, null);

        double totalVx_mps = (simVx + tangential[0]) * Simulation.LENGTH_SCALE;
        double totalVy_mps = (simVy + tangential[1]) * Simulation.LENGTH_SCALE;
        double totalVz_mps = (simVz + tangential[2]) * Simulation.LENGTH_SCALE;

        addBody(name, mass, x, y, z, totalVx_mps, totalVy_mps, totalVz_mps, radius, r, g, b);
    }

    /**
     * Add a body by Kepler elements (planet-centered). This helper
     * computes the
     * cartesian rel-position and the correct tangential velocity placed inside the
     * orbital plane.
     * a: meters, e, i (degrees), raan (deg), argp (deg), nu (deg)
     */
    private void addBodyFromKepler(String name, double mass, Body parent,
            double a, double e, double iDeg, double raanDeg, double argpDeg, double nuDeg,
            float radius, float r, float g, float b) {
        double[] rel = keplerToCartesian(a, e, iDeg, raanDeg, argpDeg, nuDeg); // meters
        double[] n = keplerOrbitNormal(iDeg, raanDeg, argpDeg);

        double simRelX = rel[0] / LENGTH_SCALE;
        double simRelY = rel[1] / LENGTH_SCALE;
        double simRelZ = rel[2] / LENGTH_SCALE;
        double px = parent.pos[0] + simRelX;
        double py = parent.pos[1] + simRelY;
        double pz = parent.pos[2] + simRelZ;

        double[] tangential = OrbitUtils.computeCircularVelocity(px, py, pz,
                parent.pos[0], parent.pos[1], parent.pos[2], parent.mass, Simulation.G_SIM, n);

        addBody(name, mass,
                px * LENGTH_SCALE, py * LENGTH_SCALE, pz * LENGTH_SCALE,
                (parent.vel[0] + tangential[0]) * LENGTH_SCALE,
                (parent.vel[1] + tangential[1]) * LENGTH_SCALE,
                (parent.vel[2] + tangential[2]) * LENGTH_SCALE,
                radius, r, g, b);
    }

    // Kepler -> Cartesian (planet-centered) in meters
    // a: semi-major axis (m)
    // e: eccentricity
    // iDeg: inclination (degrees)
    // raanDeg: longitude of ascending node Ω (degrees)
    // argpDeg: argument of periapsis ω (degrees)
    // nuDeg: true anomaly ν (degrees)
    @SuppressWarnings("unused")
    private static double[] keplerToCartesian(double a, double e,
            double iDeg, double raanDeg,
            double argpDeg, double nuDeg) {
        double i = Math.toRadians(iDeg);
        double raan = Math.toRadians(raanDeg);
        double argp = Math.toRadians(argpDeg);
        double nu = Math.toRadians(nuDeg);

        // radius in orbital plane
        double r = a * (1 - e * e) / (1 + e * Math.cos(nu));

        // position in perifocal (orbital) coordinate system
        double xP = r * Math.cos(nu);
        double yP = r * Math.sin(nu);
        double zP = 0.0;

        // Rotation: r_eci = Rz(Ω) * Rx(i) * Rz(ω) * r_perifocal
        double cosO = Math.cos(raan), sinO = Math.sin(raan);
        double cosi = Math.cos(i), sini = Math.sin(i);
        double cosw = Math.cos(argp), sinw = Math.sin(argp);

        // Components of rotation matrix (3x3)
        double r11 = cosO * cosw - sinO * sinw * cosi;
        double r12 = -cosO * sinw - sinO * cosw * cosi;
        double r13 = sinO * sini;

        double r21 = sinO * cosw + cosO * sinw * cosi;
        double r22 = -sinO * sinw + cosO * cosw * cosi;
        double r23 = -cosO * sini;

        double r31 = sinw * sini;
        double r32 = cosw * sini;
        double r33 = cosi;

        // multiply matrix by perifocal vector [xP, yP, 0]
        double x = r11 * xP + r12 * yP; // + r13*0
        double y = r21 * xP + r22 * yP; // + r23*0
        double z = r31 * xP + r32 * yP; // + r33*0

        return new double[] { x, y, z };
    }

    // compute orbital normal from classical elements (degrees). Uses same rotation
    // R = Rz(Ω) Rx(i) Rz(ω)
    @SuppressWarnings("unused")
    private static double[] keplerOrbitNormal(double iDeg, double raanDeg, double argpDeg) {
        double i = Math.toRadians(iDeg);
        double raan = Math.toRadians(raanDeg);
        double argp = Math.toRadians(argpDeg);
        double cosO = Math.cos(raan), sinO = Math.sin(raan);
        double cosi = Math.cos(i), sini = Math.sin(i);
        double cosw = Math.cos(argp), sinw = Math.sin(argp);
        // third column of R (R * (0,0,1)) gives orbital normal:
        double nx = sinO * sini;
        double ny = -cosO * sini;
        double nz = cosi;
        double nlen = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nlen < 1e-12)
            return new double[] { 0, 1, 0 };
        return new double[] { nx / nlen, ny / nlen, nz / nlen };
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
