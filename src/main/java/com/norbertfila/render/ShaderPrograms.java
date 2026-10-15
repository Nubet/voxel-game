package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ShaderPrograms {
    private ShaderPrograms() {}

    static int create(String vertexResource, String fragmentResource) {
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, vertexResource);
            fragment = compile(GL_FRAGMENT_SHADER, fragmentResource);
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException("Shader program link failed: " + glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException | Error failure) {
            if (program != 0) glDeleteProgram(program);
            throw failure;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    static int uniform(int program, String name) {
        int location = glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("Shader uniform not found: " + name);
        return location;
    }

    private static int compile(int type, String resource) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, loadSource(resource));
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
                throw new IllegalStateException(
                        "Shader compilation failed for " + resource + ": " + glGetShaderInfoLog(shader));
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    private static String loadSource(String resource) {
        try (InputStream stream = ShaderPrograms.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Shader resource not found: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read shader resource: " + resource, failure);
        }
    }
}
