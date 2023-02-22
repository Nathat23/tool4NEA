package com.invoicer.document;

import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PseudocodeConverter {

    private final CompilationUnit compilationUnit;
    private final ClassOrInterfaceDeclaration declaration;
    private final Path file;

    public PseudocodeConverter(CompilationUnit compilationUnit, ClassOrInterfaceDeclaration declaration, Path file) {
        this.compilationUnit = compilationUnit;
        this.declaration = declaration;
        this.file = file;
    }

    public ClassOrInterfaceDeclaration getDeclaration() {
        return declaration;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public List<String> convertToOcrPseudocode(MethodDeclaration methodDeclaration) {
        System.out.println(methodDeclaration.getName());
        if (methodDeclaration.getBody().isEmpty()) {
            return Collections.emptyList();
        }
        int startPos = methodDeclaration.getBegin().get().line;
        startPos = methodDeclaration.getAnnotations().isEmpty() ? startPos - 1 : startPos;
        return convertFile().subList(startPos, methodDeclaration.getEnd().get().line);
    }

    public List<String> convertFile() {
        List<String> allLines = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file.toFile()))) {
            for (String line; (line = bufferedReader.readLine()) != null;) {
                allLines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            for (MethodDeclaration methodDeclaration : classOrInterfaceDeclaration.getMethods()) {
                int startPos = methodDeclaration.getBegin().get().line;
                startPos = methodDeclaration.getAnnotations().isEmpty() ? startPos - 1 : startPos;
                StringBuilder stringBuilder = new StringBuilder(getWhitespace(allLines.get(startPos))).append(methodDeclaration.getAccessSpecifier().asString()).append(" ");
                StringBuilder end = new StringBuilder(getWhitespace(allLines.get(startPos)));
                if (methodDeclaration.getType().isVoidType()) {
                    stringBuilder.append("procedure").append(" ");
                    end.append("endprocedure");
                } else {
                    stringBuilder.append("function").append(" ");
                    end.append("endfunction");
                }
                stringBuilder.append(methodDeclaration.getNameAsString());
                stringBuilder.append("(");
                for (Parameter parameter : methodDeclaration.getParameters()) {
                    stringBuilder.append(parameter.getName()).append(",");
                }
                if (methodDeclaration.getParameters().isNonEmpty()) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                }
                stringBuilder.append(')');
                allLines.set(startPos, stringBuilder.toString());
                if (!(methodDeclaration.isDefault() || classOrInterfaceDeclaration.isInterface())) {
                    allLines.set(methodDeclaration.getEnd().get().line - 1, end.toString());
                }
            }
        }
        for (ForStmt forStmt : compilationUnit.findAll(ForStmt.class)) {
            String space = getWhitespace(allLines.get(forStmt.getBegin().get().line -1));
            String forId = space + "for " + forStmt.getInitialization().get(0) + " to " + forStmt.getCompare().get().toString().split(" ")[2];
            String endFor = space + "next " + forStmt.getInitialization().get(0).toString().split(" ")[1];
            allLines.set(forStmt.getBegin().get().line - 1, forId);
            allLines.set(forStmt.getEnd().get().line - 1, endFor);
        }
        for (IfStmt ifStmt : compilationUnit.findAll(IfStmt.class)) {
            String space = getWhitespace(allLines.get(ifStmt.getBegin().get().line -1));
            String startIf = space + "if " + ifStmt.getCondition().toString() + " then";
            String endIf = space + "endif";
            allLines.set(ifStmt.getBegin().get().line - 1, startIf);
            allLines.set(ifStmt.getEnd().get().line - 1, endIf);
        }
        for (ForEachStmt forEachStmt : compilationUnit.findAll(ForEachStmt.class)) {
            String space = getWhitespace(allLines.get(forEachStmt.getBegin().get().line -1));
            String forId = space + "for " + forEachStmt.getVariableDeclarator().getName() + " in " + forEachStmt.getIterable().toString();
            String nextId = space + "next " + forEachStmt.getVariableDeclarator().getName();
            allLines.set(forEachStmt.getBegin().get().line -1, forId);
            allLines.set(forEachStmt.getEnd().get().line -1, nextId);
        }
        for (SwitchStmt switchStmt : compilationUnit.findAll(SwitchStmt.class)) {
            String space = getWhitespace(allLines.get(switchStmt.getBegin().get().line -1));
            String switchId = space + "switch " + switchStmt.getSelector();
            String endId = space + "endswitch";
            allLines.set(switchStmt.getBegin().get().line - 1, switchId);
            allLines.set(switchStmt.getEnd().get().line - 1, endId);
        }
        for (int i = 0; i < allLines.size(); i++) {
            String strAt = allLines.get(i);
            if (strAt.isEmpty()) {
                continue;
            }
            char charAt = strAt.charAt(strAt.length() - 1);
            if (charAt == ';') {
                strAt = strAt.substring(0, strAt.length() - 1);
            }
            strAt = strAt.replace('{', ' ').replace('}', ' ');
            allLines.set(i, strAt);
        }
        return allLines;
    }

    public String getWhitespace(String string) {
        for (int i = 0; i < string.length(); i++) {
            if (!Character.isWhitespace(string.charAt(i))) {
                return string.substring(0, i);
            }
        }
        return "non";
    }
}
