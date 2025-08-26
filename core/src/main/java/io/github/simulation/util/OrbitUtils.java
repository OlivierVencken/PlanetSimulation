package io.github.simulation.util;

public final class OrbitUtils {
    private OrbitUtils() {
    }

    /**
     * Compute a circular (tangential) velocity vector for a body at (px,py,pz)
     * w.r.t. a central point (cx,cy,cz) given the central mass in simulation mass
     * units
     * and G_SIM (in simulation units).
     */
    public static double[] computeCircularVelocity(double px, double py, double pz,
            double cx, double cy, double cz,
            double centralMass, double G_SIM) {
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        double rdist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // avoid division by zero
        double safeR = Math.max(rdist, 1e-9);

        double v_circ = Math.sqrt((G_SIM) * centralMass / safeR);

        // choose an up vector and avoid parallel case
        double ux = 0.0, uy = 1.0, uz = 0.0;
        if (rdist > 0.0) {
            double dot = (dx * ux + dy * uy + dz * uz) / rdist;
            if (Math.abs(dot) > 0.9999) { // nearly parallel -> use X axis as up
                ux = 1.0;
                uy = 0.0;
                uz = 0.0;
            }
        }

        // tangent = up x radial
        double tx = uy * dz - uz * dy;
        double ty = uz * dx - ux * dz;
        double tz = ux * dy - uy * dx;

        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        // fallbacks to guarantee non-zero vector
        if (len < 1e-12) {
            if (Math.abs(dx) > Math.abs(dz)) {
                tx = -dy;
                ty = dx;
                tz = 0;
            } else {
                tx = 0;
                ty = -dz;
                tz = dy;
            }
            len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-12) {
                tx = 1;
                ty = 0;
                tz = 0;
                len = 1;
            }
        }

        // normalize and scale to circular speed
        tx = tx / len * v_circ;
        ty = ty / len * v_circ;
        tz = tz / len * v_circ;

        return new double[] { tx, ty, tz };
    }
}
