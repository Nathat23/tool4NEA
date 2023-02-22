package com.invoicer.document;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentApplication {

    public static void menu() {
        System.out.println("Specify mode for application: ");
        System.out.println("1. Convert to pseudocode.");
        System.out.println("2. Create class variables summary.");
        System.out.println("3. Create code screenshots.");
        System.out.println("4. Exit");
        System.out.print("Enter choice: ");
    }

    public static void main(String[] args) throws IOException {
        System.out.print("Specify code location: ");
        BufferedReader readPath = new BufferedReader(new InputStreamReader(System.in));
        Path start = new File(readPath.readLine()).toPath();
        System.out.println("Searching for and parsing files...");
        List<Path> paths = Files.walk(start, Integer.MAX_VALUE).filter(pred -> pred.getFileName().toString().endsWith(".java")).collect(Collectors.toList());
        List<CompilationUnit> units = new ArrayList<>();
        for (Path path : paths) {
            CompilationUnit compilationUnit = null;
            try {
                compilationUnit = LexicalPreservingPrinter.setup(StaticJavaParser.parse(path));
            } catch (IOException e) {
                e.printStackTrace();
            }
            units.add(compilationUnit);
        }
        System.out.println("Parsed " + paths.size() + " files");
        boolean running = true;
        menu();
        while (running) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            int choice = Integer.parseInt(in.readLine());
            switch (choice) {
                case 1:
                    createPseudocode(units, paths);
                    break;
                case 2:
                    createVariableSummary(units);
                    break;
                case 3:
                    paths.forEach(path -> {
                        try {
                            createClassImage(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    break;
                case 4:
                    running = false;
                    break;
            }
            menu();
        }


    }

    public static void createVariableSummary(List<CompilationUnit> units) throws IOException {
        File file = new File("variables.csv");
        FileWriter writer = new FileWriter(file);
        for (CompilationUnit compilationUnit : units) {
            for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (classOrInterfaceDeclaration.findAll(FieldDeclaration.class).isEmpty()) {
                    continue;
                }
                writer.write(classOrInterfaceDeclaration.getNameAsString() + "\n");
                writer.write("Name, Data type, Scope, Purpose\n");
                List<String> fieldList = new ArrayList<>();
                for (FieldDeclaration fieldDeclaration : classOrInterfaceDeclaration.findAll(FieldDeclaration.class)) {
                    String stringBuilder = fieldDeclaration.getVariables().get(0).getName().asString() + ":" +
                            (fieldDeclaration.isPublic() ? "Public" : "Private") + " " +
                            (fieldDeclaration.isStatic() ? "Static" : "Non-static") + " " +
                            (fieldDeclaration.isFinal() ? "Final" : "Non-final") + " ";
                    fieldList.add(stringBuilder);
                }
                for (VariableDeclarator variableDeclarator : classOrInterfaceDeclaration.findAll(VariableDeclarator.class)) {
                    writer.write(variableDeclarator.getName() + ",\"" + variableDeclarator.getType().asString() + "\",");
                    String a = null;
                    for (String field : fieldList) {
                        String[] split = field.split(":");
                        if (!split[0].equals(variableDeclarator.getName().asString())) {
                            continue;
                        }
                        a = split[1];
                    }
                    writer.write(a == null ? "Method scope" : a);
                    writer.write(",");
                    writer.write("\n");
                }
            }
        }
        writer.close();
    }

    public static void createPseudocode(List<CompilationUnit> units, List<Path> paths) throws IOException {
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                PseudocodeConverter pseudocodeConverter = new PseudocodeConverter(unit, classOrInterfaceDeclaration, paths.get(units.indexOf(unit)));
                for (MethodDeclaration methodDeclaration : classOrInterfaceDeclaration.getMethods()) {
                    List<String> converted = pseudocodeConverter.convertToOcrPseudocode(methodDeclaration);
                    if (converted.isEmpty()) {
                        System.out.println("empty");
                        continue;
                    }
                    createImage(converted, classOrInterfaceDeclaration.getNameAsString() + " " + methodDeclaration.getNameAsString(), "pseudocode");
                }
            }

        }
    }

    public static void createClassImage(Path path) throws IOException {
        List<String> result = new ArrayList<>();
        result.add(path.getFileName().toString());
        try (Stream<String> lines = Files.lines(path)) {
            result.addAll(lines.collect(Collectors.toList()));
        }
        createImage(result, path.getFileName().toString(), "class_screenshots");
    }

    public static void createImage(List<String> strings, String name, String folder) throws IOException {
        String longest = "";
        for (String string : strings) {
            if (string.length() > longest.length()) {
                longest = string;
            }
        }
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        Font font = new Font("Arial", Font.PLAIN, 24);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int width = fm.stringWidth(longest);
        int height = fm.getHeight() * strings.size();
        g2d.dispose();

        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setFont(font);
        fm = g2d.getFontMetrics();
        g2d.setColor(new Color(255,255,255));
        int i = 1;
        for (String line : strings) {
            g2d.drawString(line, 0, fm.getAscent() * i);
            i++;
        }
        g2d.dispose();
        Path of = Path.of(folder);
        if (!Files.isDirectory(of)) {
            Files.createDirectory(of);
        }
        try {
            ImageIO.write(img, "png", new File(folder + File.separator + name + ".png"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
