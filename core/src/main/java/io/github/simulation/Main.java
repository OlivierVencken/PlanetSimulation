package io.github.simulation;

import com.badlogic.gdx.ApplicationAdapter;

/**
 * Main ApplicationAdapter that delegates to Simulation.
 */
public class Main extends ApplicationAdapter {
    private Simulation simulation;

    @Override
    public void create() {
        simulation = new Simulation();
        simulation.create();
    }

    @Override
    public void render() {
        simulation.render();
    }

    @Override
    public void dispose() {
        simulation.dispose();
    }
}