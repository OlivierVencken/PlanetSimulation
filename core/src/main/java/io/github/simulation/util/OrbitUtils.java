package io.github.simulation.util;

public final class OrbitUtils {
    private OrbitUtils() {
    }

    /**
     * Compute a circular (tangential) velocity vector for a body at (px,py,pz)
     * w.r.t. a central point (cx,cy,cz) given the central mass in simulation mass
     * units and G_SIM (in simulation units).
     *
     * The returned vector is in SIMULATION UNITS (same units as positions passed in).
     *
     * orbitalNormal: optional unit vector [nx,ny,nz] describing the desired orbital plane
     * normal. If null, the routine numerically estimates a normal that is perpendicular to
     * the radius vector.
     */
    public static double[] computeCircularVelocity(double px, double py, double pz,
            double cx, double cy, double cz,
            double centralMass, double G_SIM, double[] orbitalNormal) {
        double rx = px - cx;
        double ry = py - cy;
        double rz = pz - cz;
        double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (r < 1e-12)
            return new double[] { 0.0, 0.0, 0.0 };

        // unit radial
        double ux = rx / r;
        double uy = ry / r;
        double uz = rz / r;

        // circular speed (sim units)
        double vCirc = Math.sqrt(Math.abs(G_SIM * centralMass / r));

        // determine orbital normal
        double nx = 0;
        double ny = 0;
        double nz = 0;
        if (orbitalNormal != null && orbitalNormal.length >= 3) {
            nx = orbitalNormal[0];
            ny = orbitalNormal[1];
            nz = orbitalNormal[2];
            double nlen = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nlen < 1e-12) {
                orbitalNormal = null; // fallback
            } else {
                nx /= nlen;
                ny /= nlen;
                nz /= nlen;
            }
        }

        if (orbitalNormal == null) {
            // numeric fallback: create a slightly rotated radial vector and take the cross
            // product to produce a normal that is guaranteed to be perpendicular to both.
            double dNu = 1e-3; // small angle
            double ax = 1.0, ay = 0.0, az = 0.0;
            double dot = Math.abs(ux * ax + uy * ay + uz * az);
            if (dot > 0.9) {
                ax = 0.0;
                ay = 1.0;
                az = 0.0;
            }
            double alen = Math.sqrt(ax * ax + ay * ay + az * az);
            ax /= alen;
            ay /= alen;
            az /= alen;
            double cosd = Math.cos(dNu), sind = Math.sin(dNu);
            // Rodrigues' rotation of the radial unit by small angle around axis (ax,ay,az)
            double r2x = ux * cosd + (ay * uz - az * uy) * sind + ax * (ax * ux + ay * uy + az * uz) * (1 - cosd);
            double r2y = uy * cosd + (az * ux - ax * uz) * sind + ay * (ax * ux + ay * uy + az * uz) * (1 - cosd);
            double r2z = uz * cosd + (ax * uy - ay * ux) * sind + az * (ax * ux + ay * uy + az * uz) * (1 - cosd);

            // normal = r_hat × r2_hat
            nx = uy * r2z - uz * r2y;
            ny = uz * r2x - ux * r2z;
            nz = ux * r2y - uy * r2x;
            double nlen = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nlen < 1e-12) {
                nx = 0;
                ny = 1;
                nz = 0;
            } else {
                nx /= nlen;
                ny /= nlen;
                nz /= nlen;
            }
        }

        // tangential direction = n × r_hat
        double tx = ny * uz - nz * uy;
        double ty = nz * ux - nx * uz;
        double tz = nx * uy - ny * ux;
        double tlen = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tlen < 1e-12) {
            // numeric fallback (should not happen if n not parallel to r): pick orthogonal vector
            if (Math.abs(ux) > Math.abs(uz)) {
                tx = -uy;
                ty = ux;
                tz = 0.0;
            } else {
                tx = 0.0;
                ty = -uz;
                tz = uy;
            }
            tlen = Math.sqrt(tx * tx + ty * ty + tz * tz);
        }

        tx = tx / tlen * vCirc;
        ty = ty / tlen * vCirc;
        tz = tz / tlen * vCirc;

        return new double[] { tx, ty, tz };
    }
}
