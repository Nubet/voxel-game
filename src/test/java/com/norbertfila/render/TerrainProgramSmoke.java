package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.platform.Window;
import com.norbertfila.world.RenderLayer;
import org.joml.Matrix4f;

public final class TerrainProgramSmoke {
    public static void main(String[] args) {
        try (Window window = new Window(64, 64, "Terrain shader regression")) {
            System.out.println("GPU=" + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION));
            for (String variant : new String[] {"solid", "water", "far"}) {
                String vertex = "/shaders/terrain_" + variant + ".vert";
                try (TerrainProgram program = new TerrainProgram(vertex)) {
                    program.distanceFog(DistanceFog.forDistances(4, 32));
                    var night = SkyModel.at(54000);
                    program.environment(night);
                    program.use(new Matrix4f(), 0.5f, 1.0f, 0, 0, 0);
                    int active = glGetInteger(GL_CURRENT_PROGRAM);
                    if (glGetUniformf(active, ShaderPrograms.uniform(active, "uDaylight")) != night.light())
                        throw new AssertionError("Missing night light: " + variant);
                    for (RenderLayer layer : RenderLayer.values()) program.renderLayer(layer);
                    program.coverage(0, 0, 0);
                    if (glGetError() != GL_NO_ERROR) throw new AssertionError("GL error: " + variant);
                }
                int linked = ShaderPrograms.create(vertex, "/shaders/terrain.frag");
                try {
                    if (variant.equals("water")) ShaderPrograms.uniform(linked, "uWaterTime");
                    ShaderPrograms.uniform(linked, "uDistanceFogRange");
                    ShaderPrograms.uniform(linked, "uDistanceFogColor");
                    System.out.println("PASS " + variant + " waterTime=" + glGetUniformLocation(linked, "uWaterTime"));
                } finally {
                    glDeleteProgram(linked);
                }
            }
        }
    }
}
