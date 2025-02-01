package net.vulkanmod.vulkan.shader.converter;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public abstract class CodeParser {

    /* TODO: This is not a proper parser. It just serves the purpose of converting GLSL shaders
        to solve some simple and common compatibility issues.
        Implementing an AST would be a better solution.
     */
    public static String parseCodeLine(String line) {
        ASTNode ast = parseToAST(line);
        return ast.toString();
    }

    private static ASTNode parseToAST(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, " \t\n\r\f,(){}%", true);
        LinkedList<String> tokens = new LinkedList<>();

        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }

        return buildAST(tokens);
    }

    private static ASTNode buildAST(LinkedList<String> tokens) {
        ASTNode root = new ASTNode("root");
        ASTNode current = root;

        while (!tokens.isEmpty()) {
            String token = tokens.poll();

            if (token.equals("%")) {
                ASTNode modNode = new ASTNode("mod");
                modNode.addChild(current.removeLastChild());
                modNode.addChild(new ASTNode(tokens.poll()));
                current.addChild(modNode);
            } else {
                current.addChild(new ASTNode(checkTokenMapping(token)));
            }
        }

        return root;
    }

    private static String checkTokenMapping(String token) {
        return switch (token) {
            case "gl_VertexID" -> "gl_VertexIndex";
            default -> token;
        };
    }

    private static class ASTNode {
        private final String value;
        private final List<ASTNode> children = new LinkedList<>();

        public ASTNode(String value) {
            this.value = value;
        }

        public void addChild(ASTNode child) {
            children.add(child);
        }

        public ASTNode removeLastChild() {
            return children.remove(children.size() - 1);
        }

        @Override
        public String toString() {
            if (children.isEmpty()) {
                return value;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(value).append("(");
            for (int i = 0; i < children.size(); i++) {
                sb.append(children.get(i).toString());
                if (i < children.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
