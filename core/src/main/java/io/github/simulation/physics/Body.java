package io.github.simulation.physics;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Data holder for a body.
 */
public class Body {
    public String name;
    public double mass;
    public double[] pos = new double[3];
    public double[] vel = new double[3];
    public float radius;
    public ModelInstance instance;

    // color (0..1)
    public final float cr, cg, cb;

    // trail - time stamped points (simulation time)
    public static class TrailPoint {
        public final Vector3 p;
        public final float simTime; // sample time (seconds, simulation time)

        public TrailPoint(Vector3 p, float simTime) {
            this.p = p;
            this.simTime = simTime;
        }
    }

    private final Array<TrailPoint> trail = new Array<>();
    private float lastSampleSimTime = -Float.MAX_VALUE; // for sampling control

    public float getLastSampleSimTime() {
        return lastSampleSimTime;
    }

    public void setLastSampleSimTime(float t) {
        lastSampleSimTime = t;
    }

    public Body(String name, double mass, double x, double y, double z,
            double vx, double vy, double vz, float radius, ModelInstance instance,
            float cr, float cg, float cb) {
        this.name = name;
        this.mass = mass;
        this.pos[0] = x;
        this.pos[1] = y;
        this.pos[2] = z;
        this.vel[0] = vx;
        this.vel[1] = vy;
        this.vel[2] = vz;
        this.radius = radius;
        this.instance = instance;
        this.cr = cr;
        this.cg = cg;
        this.cb = cb;
        Matrix4 t = new Matrix4().setToTranslation((float) x, (float) y, (float) z);
        this.instance.transform.set(t);
    }

    // Trail sampling now handled by TrailRenderer

    public Array<TrailPoint> getTrail() {
        return trail;
    }
}
