package io.github.simulation.input;

import com.badlogic.gdx.utils.Array;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import io.github.simulation.Simulation;
import io.github.simulation.physics.Body;

import com.badlogic.gdx.graphics.PerspectiveCamera;

/**
 * First-person controller: WSAD movement, SHIFT=up, CTRL=down, mouse look when
 * cursor is catched.
 */
public class FirstPersonCameraController extends InputAdapter {

    private final Simulation sim;
    private final PerspectiveCamera camera;
    private boolean forward, back, left, right, up, down;
    private float yaw; // radians
    private float pitch; // radians
    private float mouseSensitivity = 0.0025f;
    private float moveSpeed = 50f;
    private boolean cursorCatched = true;
    private boolean ignoreNextMouseDelta = true; // consume first delta after capture

    // Orbit/focus/zoom state
    private int focusedBodyIndex = -1;
    private float focusYaw = 45f; // degrees
    private float focusPitch = 20f; // degrees
    private float focusZoomDistance = 40f;
    private static final float DEFAULT_FOCUS_FRACTION = 0.6f; // fraction of screen height the planet should occupy
    private static final float MIN_DISTANCE_FACTOR = 1.2f; // minimum distance = radius * factor
    private static final float ZOOM_SENSITIVITY = 0.15f; // proportional zoom per scroll unit

    private static final float MAX_MOUSE_DELTA = 200f; // pixels, clamp to avoid jumps

    public FirstPersonCameraController(Simulation sim, PerspectiveCamera camera) {
        this.sim = sim;
        this.camera = camera;
        Vector3 d = camera.direction;
        yaw = (float) Math.atan2(d.x, d.z);
        pitch = (float) Math.asin(MathUtils.clamp(d.y / d.len(), -0.999f, 0.999f));
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.W:
                forward = true;
                return true;
            case Input.Keys.S:
                back = true;
                return true;
            case Input.Keys.A:
                left = true;
                return true;
            case Input.Keys.D:
                right = true;
                return true;
            case Input.Keys.SHIFT_LEFT:
            case Input.Keys.SHIFT_RIGHT:
                up = true;
                return true;
            case Input.Keys.CONTROL_LEFT:
            case Input.Keys.CONTROL_RIGHT:
                down = true;
                return true;
            case Input.Keys.M:
                cursorCatched = !cursorCatched;
                Gdx.input.setCursorCatched(cursorCatched);
                ignoreNextMouseDelta = true; // consume next delta to avoid jump
                return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.W:
                forward = false;
                return true;
            case Input.Keys.S:
                back = false;
                return true;
            case Input.Keys.A:
                left = false;
                return true;
            case Input.Keys.D:
                right = false;
                return true;
            case Input.Keys.SHIFT_LEFT:
            case Input.Keys.SHIFT_RIGHT:
                up = false;
                return true;
            case Input.Keys.CONTROL_LEFT:
            case Input.Keys.CONTROL_RIGHT:
                down = false;
                return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (sim.isFocusedOnBody()) {
            zoomFocusedBody(amountY * 3f); // scroll up: zoom in, scroll down: zoom out
            return true;
        }
        return false;
    }

    public void update(float delta) {
        if (Gdx.input.isCursorCatched()) {
            updateMouseLook();
        }

        if (sim.isFocusedOnBody()) {
            updateFocusedMovement(sim.getBodies(), delta);
        } else {
            updateMovement(delta);
        }

        camera.update();
    }

    public void updateMouseLook() {
        float rawDx = -Gdx.input.getDeltaX();
        float rawDy = -Gdx.input.getDeltaY();

        // consume first delta after capture
        if (ignoreNextMouseDelta) {
            rawDx = 0f;
            rawDy = 0f;
            ignoreNextMouseDelta = false;
        }

        // clamp to avoid one-shot huge jumps
        float dx = MathUtils.clamp(rawDx, -MAX_MOUSE_DELTA, MAX_MOUSE_DELTA);
        float dy = MathUtils.clamp(rawDy, -MAX_MOUSE_DELTA, MAX_MOUSE_DELTA);

        yaw += dx * mouseSensitivity;
        pitch += dy * mouseSensitivity;
        pitch = MathUtils.clamp(pitch, -MathUtils.degreesToRadians * 89f, MathUtils.degreesToRadians * 89f);

        float cosPitch = MathUtils.cos(pitch);
        camera.direction.x = cosPitch * MathUtils.sin(yaw);
        camera.direction.y = MathUtils.sin(pitch);
        camera.direction.z = cosPitch * MathUtils.cos(yaw);
        camera.up.set(Vector3.Y);
    }

    public void updateMovement(float delta) {
        // movement: project forward onto XZ plane so movement doesn't fly when looking
        // up
        Vector3 forwardDir = new Vector3(camera.direction).nor();
        forwardDir.y = 0;
        forwardDir.nor();
        Vector3 rightDir = new Vector3(forwardDir).crs(camera.up).nor();

        Vector3 movement = new Vector3();
        if (forward)
            movement.add(new Vector3(forwardDir));
        if (back)
            movement.add(new Vector3(forwardDir).scl(-1f));
        if (right)
            movement.add(new Vector3(rightDir));
        if (left)
            movement.add(new Vector3(rightDir).scl(-1f));
        if (up)
            movement.add(new Vector3(Vector3.Y));
        if (down)
            movement.add(new Vector3(Vector3.Y).scl(-1f));

        if (!movement.isZero()) {
            movement.nor().scl(moveSpeed * delta);
            camera.position.add(movement);
        }
    }

    public void updateFocusedMovement(Array<Body> bodies, float delta) {
        if (focusedBodyIndex >= 0 && focusedBodyIndex < bodies.size) {
            io.github.simulation.physics.Body b = bodies.get(focusedBodyIndex);
            // Ensure focusZoomDistance is computed relative to body radius so framing is consistent
            float r = focusZoomDistance;
            if (r <= 0f) {
                r = computeFocusDistance(b);
                focusZoomDistance = r;
            }
            // Clamp pitch to avoid passing over the poles
            focusPitch = MathUtils.clamp(focusPitch, 0.1f, 179.9f);
            float yawRad = MathUtils.degreesToRadians * focusYaw;
            float pitchRad = MathUtils.degreesToRadians * focusPitch;
            float x = (float) b.pos[0] + r * MathUtils.cos(pitchRad) * MathUtils.cos(yawRad);
            float y = (float) b.pos[1] + r * MathUtils.sin(pitchRad);
            float z = (float) b.pos[2] + r * MathUtils.cos(pitchRad) * MathUtils.sin(yawRad);

            if (forward)
                orbitCamera(0f, moveSpeed * delta); // up
            if (back)
                orbitCamera(0f, -moveSpeed * delta); // down
            if (right)
                orbitCamera(moveSpeed * delta, 0f); // right
            if (left)
                orbitCamera(-moveSpeed * delta, 0f); // left

            camera.position.set(x, y, z);
            camera.lookAt((float) b.pos[0], (float) b.pos[1], (float) b.pos[2]);
            // Prevent up vector flipping near the poles
            float poleThreshold = 2.0f; // degrees from pole
            if (focusPitch < poleThreshold || focusPitch > (180f - poleThreshold)) {
                camera.up.set(Vector3.Y);
            } else {
                Vector3 lookDir = new Vector3((float) b.pos[0], (float) b.pos[1], (float) b.pos[2]).sub(camera.position)
                        .nor();
                Vector3 right = new Vector3(lookDir).crs(Vector3.Y).nor();
                Vector3 up = new Vector3(right).crs(lookDir).nor();
                camera.up.set(up);
            }
        }
    }

    private float computeFocusDistance(Body b) {
        // b.radius is in simulation length units; compute a camera distance so the body
        // fills roughly DEFAULT_FOCUS_FRACTION of the vertical FOV.
        float R = (float) b.radius; // sim units
        float fovRad = MathUtils.degreesToRadians * camera.fieldOfView;
        float halfView = fovRad * DEFAULT_FOCUS_FRACTION * 0.5f;
        float sinHalf = MathUtils.sin(halfView);
        float desired;
        if (sinHalf <= 1e-6f) {
            desired = R * MIN_DISTANCE_FACTOR;
        } else {
            desired = R / sinHalf;
            desired = Math.max(desired, R * MIN_DISTANCE_FACTOR);
        }
        return desired;
    }

    public boolean isFocusedOnBody() {
        return focusedBodyIndex >= 0;
    }

    public void cycleCameraFocus(Array<io.github.simulation.physics.Body> bodies) {
        if (bodies.size == 0) {
            clearFocus();
            return;
        }
        focusedBodyIndex = (focusedBodyIndex + 1) % bodies.size;
    }

    public void setFocus(int bodyIndex) {
        focusedBodyIndex = bodyIndex;
        if (focusedBodyIndex >= 0) {
            Array<io.github.simulation.physics.Body> bodies = sim.getBodies();
            if (focusedBodyIndex < bodies.size) {
                Body b = bodies.get(focusedBodyIndex);
                focusZoomDistance = computeFocusDistance(b);
            }
        }
    }

    public void clearFocus() {
        focusedBodyIndex = -1;
    }

    public void orbitCamera(float deltaYaw, float deltaPitch) {
        focusYaw += deltaYaw;
        focusPitch = MathUtils.clamp(focusPitch + deltaPitch, 0.1f, 179.9f);
    }

    public void zoomFocusedBody(float delta) {
    // Make zoom proportional to current distance so it feels consistent across scales
    float factor = 1f + delta * ZOOM_SENSITIVITY;
    factor = MathUtils.clamp(factor, 0.01f, 10f);
    focusZoomDistance = Math.max(0.01f, focusZoomDistance * factor);
    }

    public int getFocusedBodyIndex() {
        return focusedBodyIndex;
    }
}