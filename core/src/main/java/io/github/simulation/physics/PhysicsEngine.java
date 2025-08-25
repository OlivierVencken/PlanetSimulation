package io.github.simulation.physics;

import com.badlogic.gdx.utils.Array;
import io.github.simulation.Simulation;

/**
 * Simple physics engine using Velocity Verlet integration.
 */
public class PhysicsEngine {
    private final Array<Body> bodies;

    public PhysicsEngine(Array<Body> bodies) {
        this.bodies = bodies;
    }

    public void integrate(double dt) {
        final int n = bodies.size;
        if (n == 0)
            return;

        double[][] aOld = new double[n][3];
        for (int i = 0; i < n; i++) {
            double[] acc = computeAccelerationOn(i);
            aOld[i][0] = acc[0];
            aOld[i][1] = acc[1];
            aOld[i][2] = acc[2];
        }

        for (int i = 0; i < n; i++) {
            Body b = bodies.get(i);
            b.pos[0] += b.vel[0] * dt + 0.5 * aOld[i][0] * dt * dt;
            b.pos[1] += b.vel[1] * dt + 0.5 * aOld[i][1] * dt * dt;
            b.pos[2] += b.vel[2] * dt + 0.5 * aOld[i][2] * dt * dt;
        }

        double[][] aNew = new double[n][3];
        for (int i = 0; i < n; i++) {
            double[] acc = computeAccelerationOn(i);
            aNew[i][0] = acc[0];
            aNew[i][1] = acc[1];
            aNew[i][2] = acc[2];
        }

        for (int i = 0; i < n; i++) {
            Body b = bodies.get(i);
            b.vel[0] += 0.5 * (aOld[i][0] + aNew[i][0]) * dt;
            b.vel[1] += 0.5 * (aOld[i][1] + aNew[i][1]) * dt;
            b.vel[2] += 0.5 * (aOld[i][2] + aNew[i][2]) * dt;
        }
    }

    private double[] computeAccelerationOn(int i) {
        Body bi = bodies.get(i);
        double ax = 0, ay = 0, az = 0;
        for (int j = 0; j < bodies.size; j++) {
            if (i == j)
                continue;
            Body bj = bodies.get(j);
            double dx = bj.pos[0] - bi.pos[0];
            double dy = bj.pos[1] - bi.pos[1];
            double dz = bj.pos[2] - bi.pos[2];
            double r2 = dx * dx + dy * dy + dz * dz + Simulation.SOFTENING * Simulation.SOFTENING;
            double invR3 = 1.0 / (Math.sqrt(r2) * r2);
            // bj.mass is stored in simulation mass units; use G_SIM which is in sim units
            double factor = (Simulation.G_SIM) * bj.mass * invR3;
            ax += factor * dx;
            ay += factor * dy;
            az += factor * dz;
        }
        return new double[] { ax, ay, az };
    }
}