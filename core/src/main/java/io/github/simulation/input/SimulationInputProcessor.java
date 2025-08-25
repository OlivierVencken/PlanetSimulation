
package io.github.simulation.input;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input;

import io.github.simulation.Simulation;

/**
 * Input processor for simulation controls:
 * - Pauses/resumes the simulation
 * - Steps the simulation forward
 * - Cycles camera focus
 * - Resets the simulation
 * - Adjusts time scale
 * - Toggles trails and grid visibility
 */
public class SimulationInputProcessor extends InputAdapter {

    private final Simulation sim;

    public SimulationInputProcessor(Simulation sim) {
        this.sim = sim;
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.SPACE:
                sim.setPaused(!sim.isPaused());
                return true;
            case Input.Keys.N:
                sim.singleStep();
                return true;
            case Input.Keys.F:
                sim.getFpsController().cycleCameraFocus(sim.getBodies());
                return true;
            case Input.Keys.C:
                if (sim.getFpsController().isFocusedOnBody()) {
                    sim.getFpsController().clearFocus();
                }
                return true;
            case Input.Keys.R:
                sim.reset();
                return true;
            case Input.Keys.PLUS:
            case Input.Keys.EQUALS:
                sim.increaseTimeScale();
                return true;
            case Input.Keys.MINUS:
                sim.decreaseTimeScale();
                return true;
            case Input.Keys.T:
                sim.setTrailsEnabled(!sim.isTrailsEnabled());
                return true;
            case Input.Keys.G:
                sim.setGridShown(!sim.isGridShown());
                return true;
            case Input.Keys.ESCAPE:
                System.exit(0);
                return true;
        }
        return false;
    }
}