package me.tomassetti.turin.compiler;

import com.google.common.collect.ImmutableList;
import me.tomassetti.turin.classloading.TurinClassLoader;
import me.tomassetti.turin.classloading.ClassFileDefinition;
import me.tomassetti.turin.resolvers.InFileSymbolResolver;
import me.tomassetti.turin.resolvers.jdk.JdkTypeResolver;
import me.tomassetti.turin.parser.ast.*;
import me.tomassetti.turin.parser.ast.expressions.*;
import me.tomassetti.turin.parser.ast.expressions.literals.StringLiteral;
import me.tomassetti.turin.parser.ast.statements.BlockStatement;
import me.tomassetti.turin.parser.ast.statements.ExpressionStatement;
import me.tomassetti.turin.parser.ast.statements.Statement;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * We test specifically the compilation of programs AST nodes.
 */
public class CompilerOnProgramTest extends AbstractCompilerTest {

    private TurinFile emptyProgram() {
        // define AST
        TurinFile turinFile = new TurinFile();

        NamespaceDefinition namespaceDefinition = new NamespaceDefinition("myProgram");

        turinFile.setNameSpace(namespaceDefinition);

        Program program = new Program("SuperSimple", new BlockStatement(ImmutableList.of()), "args");
        program.setPosition(Position.create(0, 0, 0, 0));
        turinFile.add(program);

        return turinFile;
    }

    private TurinFile simpleProgram() {
        // define AST
        TurinFile turinFile = new TurinFile();

        NamespaceDefinition namespaceDefinition = new NamespaceDefinition("myProgram");

        turinFile.setNameSpace(namespaceDefinition);

        StringLiteral stringLiteral = new StringLiteral("Hello Turin!");
        QualifiedName javaLang = new QualifiedName(new QualifiedName("java"), "lang");
        TypeIdentifier system = new TypeIdentifier(javaLang, "System");
        StaticFieldAccess out = new StaticFieldAccess(system, "out");
        FieldAccess println = new FieldAccess(out, "println");
        FunctionCall printInvokation = new FunctionCall(println, ImmutableList.of(new ActualParam(stringLiteral)));
        Statement printStatement = new ExpressionStatement(printInvokation);
        Program program = new Program("SuperSimple", new BlockStatement(ImmutableList.of(printStatement)), "args");
        program.setPosition(Position.create(0, 0, 0, 0));
        turinFile.add(program);

        return turinFile;
    }

    private void invokeProgram(Class<?> programClass) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method main = programClass.getMethod("main", String[].class);
        assertEquals("main", main.getName());
        assertEquals(true, Modifier.isStatic(main.getModifiers()));
        assertEquals(1, main.getParameterTypes().length);
        assertEquals(String[].class, main.getParameterTypes()[0]);
        main.invoke(null, (Object)new String[]{});
    }

    private void loadAndInvoke(ClassFileDefinition classFileDefinition) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        TurinClassLoader turinClassLoader = new TurinClassLoader();
        Class programClass = turinClassLoader.addClass(classFileDefinition.getName(),
                classFileDefinition.getBytecode());
        invokeProgram(programClass);
    }

    @Test
    public void compileAnEmptyProgram() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        TurinFile turinFile = emptyProgram();

        // generate bytecode
        Compiler instance = new Compiler(new InFileSymbolResolver(JdkTypeResolver.getInstance()), new Compiler.Options());
        List<ClassFileDefinition> classFileDefinitions = instance.compile(turinFile, new MyErrorCollector());
        assertEquals(1, classFileDefinitions.size());

        loadAndInvoke(classFileDefinitions.get(0));
    }

    @Test
    public void compileASimpleProgram() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        TurinFile turinFile = simpleProgram();

        // generate bytecode
        Compiler instance = new Compiler(new InFileSymbolResolver(JdkTypeResolver.getInstance()), new Compiler.Options());
        List<ClassFileDefinition> classFileDefinitions = instance.compile(turinFile, new MyErrorCollector());
        assertEquals(1, classFileDefinitions.size());
        assertEquals("myProgram.SuperSimple", classFileDefinitions.get(0).getName());

        loadAndInvoke(classFileDefinitions.get(0));
    }


}
