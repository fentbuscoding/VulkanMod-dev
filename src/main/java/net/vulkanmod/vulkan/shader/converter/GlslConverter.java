package net.vulkanmod.vulkan.shader.converter;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

import java.util.*;

public class GlslConverter {
    
    ShaderStage shaderStage;
    private State state;

    private UniformParser uniformParser;
    private InputOutputParser inOutParser;

    private String vshConverted;
    private String fshConverted;

    public void process(String vertShader, String fragShader) {
        this.uniformParser = new UniformParser(this);
        this.inOutParser = new InputOutputParser(this);

        StringBuilder vshOut = this.processShaderFile(ShaderStage.Vertex, vertShader);
        vshOut.insert(0, this.inOutParser.createInOutCode());

        StringBuilder fshOut = this.processShaderFile(ShaderStage.Fragment, fragShader);
        fshOut.insert(0, this.inOutParser.createInOutCode());

        String uniformBlock = this.uniformParser.createUniformsCode();
        vshOut.insert(0, uniformBlock);
        fshOut.insert(0, uniformBlock);

        String samplersVertCode = this.uniformParser.createSamplersCode(ShaderStage.Vertex);
        String samplersFragCode = this.uniformParser.createSamplersCode(ShaderStage.Fragment);

        vshOut.insert(0, samplersVertCode);
        fshOut.insert(0, samplersFragCode);

        vshOut.insert(0, "#version 450\n\n");
        fshOut.insert(0, "#define sample sample1\n");
        fshOut.insert(0, "#version 450\n\n");

        this.vshConverted = vshOut.toString();
        this.fshConverted = fshOut.toString();

    }

    private StringBuilder processShaderFile(ShaderStage stage, String shader) {
        this.setShaderStage(stage);

        String[] lines = shader.split("\n");
        var out = new StringBuilder();

        var iterator = Arrays.stream(lines).iterator();

        while (iterator.hasNext()) {
            String line = iterator.next();

            int semicolons = charOccurences(line, ';');

            if (semicolons > 1) {
                var lines2 = line.splitWithDelimiters(";", 0);

                int matchingFor = 0;
                for (int i = 0; i < lines2.length;) {
                    StringBuilder line2 = new StringBuilder(lines2[i]);
                    i++;

                    matchingFor += charOccurences(line2.toString(), '(');
                    matchingFor -= charOccurences(line2.toString(), ')');

                    while (matchingFor > 0) {
                        String next = lines2[i];
                        i++;

                        matchingFor += charOccurences(next, '(');
                        matchingFor -= charOccurences(next, ')');

                        line2.append(next);
                    }

                    if (i < lines2.length) {
                        line2.append(lines2[i]);
                        i++;
                    }


                    if (matchingFor == 0) {
                        String parsedLine = this.parseLine(line2.toString());
                        if (parsedLine != null) {
                            out.append(parsedLine);
                            out.append("\n");
                        }
                    }

                }
            }
            else {
                String parsedLine = this.parseLine(line);
                if (parsedLine != null) {
                    out.append(parsedLine);
                    out.append("\n");
                }
            }

        }

        return out;
    }

    private int charOccurences(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }

    private String parseLine(String line) {
        StringBuilder tokenizer = new StringBuilder(line);

        // empty line
        if (tokenizer.length() == 0)
            return "\n";

        int firstSpace = tokenizer.indexOf(" ");
        if (firstSpace == -1) {
            throw new IllegalArgumentException("Less than 3 tokens present");
        }

        String token = tokenizer.substring(0, firstSpace);
        tokenizer.delete(0, firstSpace + 1);

        switch (token) {
            case "uniform" -> this.state = State.MATCHING_UNIFORM;
            case "in", "out" -> this.state = State.MATCHING_IN_OUT;
            case "#version" -> {
                return null;
            }
            case "#moj_import" -> {
                if (tokenizer.indexOf(" ") != -1) {
                    throw new IllegalArgumentException("Token count != 1");
                }

                return String.format("#include %s", tokenizer.toString().trim());
            }

            default -> {
                return CodeParser.parseCodeLine(line);
            }
        }

        if (tokenizer.indexOf(" ") == -1) {
            throw new IllegalArgumentException("Less than 3 tokens present");
        }

        feedToken(token);

        while (tokenizer.length() > 0) {
            int nextSpace = tokenizer.indexOf(" ");
            if (nextSpace == -1) {
                feedToken(tokenizer.toString().trim());
                break;
            }

            token = tokenizer.substring(0, nextSpace);
            tokenizer.delete(0, nextSpace + 1);

            feedToken(token);
        }

        return null;
    }

    private void feedToken(String token) {
        switch (this.state) {
            case MATCHING_UNIFORM -> this.uniformParser.parseToken(token);
            case MATCHING_IN_OUT -> this.inOutParser.parseToken(token);
        }
    }

    private void setShaderStage(ShaderStage shaderStage) {
        this.shaderStage = shaderStage;
        this.uniformParser.setCurrentUniforms(this.shaderStage);
        this.inOutParser.setShaderStage(this.shaderStage);
    }

    public UBO createUBO() {
        return this.uniformParser.createUBO();
    }

    public List<ImageDescriptor> getSamplerList() {
        return this.uniformParser.getSamplers();
    }

    public String getVshConverted() {
        return vshConverted;
    }

    public String getFshConverted() {
        return fshConverted;
    }

    enum ShaderStage {
        Vertex,
        Fragment
    }

    enum State {
        MATCHING_UNIFORM,
        MATCHING_IN_OUT
    }
}
