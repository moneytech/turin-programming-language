package me.tomassetti.turin.compiler;

import com.google.common.collect.ImmutableList;
import me.tomassetti.turin.classloading.TurinClassLoader;
import me.tomassetti.turin.classloading.ClassFileDefinition;
import me.tomassetti.turin.compiler.errorhandling.ErrorCollector;
import me.tomassetti.turin.parser.Parser;
import me.tomassetti.turin.resolvers.*;
import me.tomassetti.turin.resolvers.compiled.DirClassesTypeResolver;
import me.tomassetti.turin.resolvers.compiled.JarTypeResolver;
import me.tomassetti.turin.resolvers.jdk.JdkTypeResolver;
import me.tomassetti.turin.parser.ast.Position;
import me.tomassetti.turin.parser.ast.TurinFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public abstract class AbstractCompilerTest {

    public static class MyErrorCollector implements ErrorCollector {

        @Override
        public void recordSemanticError(Position position, String description) {
            if (position == null) {
                throw new IllegalArgumentException("null position");
            }
            throw new RuntimeException(position.toString() + " : " + description);
        }
    }

    // Used for debugging
    protected static void saveClassFile(ClassFileDefinition classFileDefinition, String dir) {
        File output = null;
        try {
            output = new File(dir + "/" + classFileDefinition.getName().replaceAll("\\.", "/") + ".class");
            output.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(output);
            fos.write(classFileDefinition.getBytecode());
        } catch (IOException e) {
            System.err.println("Problem writing file "+output+": "+ e.getMessage());
            System.exit(3);
        }
    }

    protected SymbolResolver getResolverFor(TurinFile turinFile) {
        try {
            TypeResolver turinStdLib = new JarTypeResolver(new File("../turin-standard-library/target/turin-standard-library-0.0.3-SNAPSHOT.jar"));
            return new ComposedSymbolResolver(ImmutableList.of(
                    new InFileSymbolResolver(new ComposedTypeResolver(ImmutableList.of(
                            JdkTypeResolver.getInstance(),
                            turinStdLib))),
                    new SrcSymbolResolver(ImmutableList.of(turinFile))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected SymbolResolver getResolverFor(TurinFile turinFile, List<String> jarFiles) {
        return getResolverFor(turinFile, jarFiles, Collections.emptyList());
    }

    protected SymbolResolver getResolverFor(TurinFile turinFile, List<String> jarFiles, List<String> classesDirs) {
        return getResolverFor(ImmutableList.of(turinFile), jarFiles, classesDirs);
    }

    protected SymbolResolver getResolverFor(List<TurinFile> turinFiles, List<String> jarFiles, List<String> classesDirs) {
        jarFiles = new ArrayList<>(jarFiles);
        jarFiles.add("../turin-standard-library/target/turin-standard-library-0.0.3-SNAPSHOT.jar");
        TypeResolver typeResolver = new ComposedTypeResolver(ImmutableList.<TypeResolver>builder()
                .add(JdkTypeResolver.getInstance())
                .addAll(jarFiles.stream().map((jf) -> {
                    try {
                        return new JarTypeResolver(new File(jf));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList()))
                .addAll(classesDirs.stream().map((d) -> {
                    try {
                        return new DirClassesTypeResolver(new File(d));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList()))
                .build());
        return new ComposedSymbolResolver(ImmutableList.of(new InFileSymbolResolver(typeResolver), new SrcSymbolResolver(turinFiles)));
    }

    public Method compileFunction(String exampleName, Class[] paramTypes) throws NoSuchMethodException, IOException {
        return compileFunction(exampleName, paramTypes, Collections.emptyList());
    }

    protected ErrorCollector getErrorCollector() {
        return new MyErrorCollector();
    }

    public Class compileType(String exampleName, List<String> classPathElements) throws NoSuchMethodException, IOException {
        TurinFile turinFile = new Parser().parse(this.getClass().getResourceAsStream("/" + exampleName + ".to"));

        // generate bytecode
        Compiler.Options options = new Compiler.Options();
        options.setClassPathElements(classPathElements);
        Compiler instance = new Compiler(getResolverFor(turinFile, classPathElements), options);
        List<ClassFileDefinition> classFileDefinitions = instance.compile(turinFile, getErrorCollector());
        saveClassFile(classFileDefinitions.get(0), "tmp");
        assertEquals(1, classFileDefinitions.size());

        TurinClassLoader turinClassLoader = new TurinClassLoader();
        Class clazz = turinClassLoader.addClass(classFileDefinitions.get(0).getName(),
                classFileDefinitions.get(0).getBytecode());
        return clazz;
    }

    public Method compileFunction(String exampleName, Class[] paramTypes, List<String> classPathElements) throws NoSuchMethodException, IOException {
        Class functionClass = compileType(exampleName, classPathElements);
        assertEquals(0, functionClass.getConstructors().length);

        Method invoke = functionClass.getMethod("invoke", paramTypes);
        return invoke;
    }

    public void attemptToCompile(String exampleName, List<String> classPathElements) throws NoSuchMethodException, IOException {
        InputStream is = this.getClass().getResourceAsStream("/" + exampleName + ".to");
        if (is == null) {
            throw new RuntimeException();
        }
        TurinFile turinFile = new Parser().parse(is);

        // generate bytecode
        Compiler.Options options = new Compiler.Options();
        options.setClassPathElements(classPathElements);
        Compiler instance = new Compiler(getResolverFor(turinFile, classPathElements), options);
        List<ClassFileDefinition> classFileDefinitions = instance.compile(turinFile, getErrorCollector());
    }

    protected Optional<Throwable> getException(Method functionMethod) throws IllegalAccessException {
        try {
            functionMethod.invoke(null);
            return Optional.empty();
        } catch (InvocationTargetException e) {
            return Optional.of(e.getTargetException());
        }
    }
}
