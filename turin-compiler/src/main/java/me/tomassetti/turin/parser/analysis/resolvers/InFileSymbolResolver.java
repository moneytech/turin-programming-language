package me.tomassetti.turin.parser.analysis.resolvers;

import me.tomassetti.turin.compiler.SemanticErrorException;
import me.tomassetti.turin.jvm.JvmMethodDefinition;
import me.tomassetti.turin.jvm.JvmNameUtils;
import me.tomassetti.turin.jvm.JvmType;
import me.tomassetti.turin.parser.analysis.UnsolvedSymbolException;
import me.tomassetti.turin.parser.ast.*;
import me.tomassetti.turin.implicit.BasicTypeUsage;
import me.tomassetti.turin.parser.ast.expressions.Expression;
import me.tomassetti.turin.parser.ast.expressions.FunctionCall;
import me.tomassetti.turin.parser.ast.expressions.StaticFieldAccess;
import me.tomassetti.turin.parser.ast.imports.ImportDeclaration;
import me.tomassetti.turin.parser.ast.typeusage.PrimitiveTypeUsage;
import me.tomassetti.turin.parser.ast.typeusage.ReferenceTypeUsage;
import me.tomassetti.turin.parser.ast.typeusage.TypeUsage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolve symbols by looking in the file where the context node is contained.
 */
public class InFileSymbolResolver implements SymbolResolver {

    private TypeResolver typeResolver;

    public InFileSymbolResolver(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    @Override
    public Optional<PropertyDefinition> findDefinition(PropertyReference propertyReference) {
        return findDefinitionIn(propertyReference, propertyReference.getParent());
    }

    private Optional<PropertyDefinition> findDefinitionIn(PropertyReference propertyReference, Node context) {
        for (Node child : context.getChildren()) {
            if (child instanceof PropertyDefinition) {
                PropertyDefinition propertyDefinition = (PropertyDefinition)child;
                if (propertyDefinition.getName().equals(propertyReference.getName())) {
                    return Optional.of(propertyDefinition);
                }
            }
        }
        if (context.getParent() == null) {
            return Optional.empty();
        }
        return findDefinitionIn(propertyReference, context.getParent());
    }

    @Override
    public Optional<TypeDefinition> findTypeDefinitionIn(String typeName, Node context, SymbolResolver resolver) {
        // primitive names are not valid here
        if (!JvmNameUtils.isValidQualifiedName(typeName)) {
            throw new IllegalArgumentException(typeName);
        }
        return findTypeDefinitionInHelper(typeName, context, resolver);
    }

    @Override
    public TypeUsage findTypeUsageIn(String typeName, Node context, SymbolResolver resolver) {
        if (PrimitiveTypeUsage.isPrimitiveTypeName(typeName)){
            return PrimitiveTypeUsage.getByName(typeName);
        }
        // note that this check has to come after the check for primitive types
        if (!JvmNameUtils.isValidQualifiedName(typeName)) {
            throw new IllegalArgumentException(typeName);
        }

        // Note that our Turin basic types could shadow other types
        Optional<BasicTypeUsage> basicType = BasicTypeUsage.findByName(typeName);
        if (basicType.isPresent()) {
            return basicType.get();
        }

        return new ReferenceTypeUsage(getTypeDefinitionIn(typeName, context, resolver));
    }

    @Override
    public JvmMethodDefinition findJvmDefinition(FunctionCall functionCall) {
        List<JvmType> argsTypes = functionCall.getActualParamValuesInOrder().stream()
                .map((ap) -> ap.calcType(this).jvmType(this))
                .collect(Collectors.toList());
        Expression function = functionCall.getFunction();
        boolean staticContext = function.isType(this) || (function instanceof StaticFieldAccess);
        return function.findMethodFor(argsTypes, this, staticContext);
    }

    @Override
    public Optional<Node> findSymbol(String name, Node context) {
        return context.findSymbol(name, this);
    }

    private Optional<TypeDefinition> findTypeDefinitionInHelper(String typeName, Node context, SymbolResolver resolver) {
        if (!JvmNameUtils.isValidQualifiedName(typeName)) {
            throw new IllegalArgumentException(typeName);
        }
        for (Node child : context.getChildren()) {
            if (child instanceof TypeDefinition) {
                TypeDefinition typeDefinition = (TypeDefinition)child;
                if (typeDefinition.getName().equals(typeName)
                        || typeDefinition.getQualifiedName().equals(typeName)) {
                    return Optional.of(typeDefinition);
                }
            } else if (child instanceof ImportDeclaration) {
                ImportDeclaration importDeclaration = (ImportDeclaration)child;
                Optional<Node> resolvedNode = importDeclaration.findAmongImported(typeName, resolver);
                if (resolvedNode.isPresent()) {
                    if (resolvedNode.get() instanceof TypeDefinition) {
                        return Optional.of((TypeDefinition)resolvedNode.get());
                    } else {
                        throw new SemanticErrorException(context, "" + typeName + " is not a type");
                    }
                }
            }
        }
        if (context.getParent() == null) {
            // implicitly look into java.lang package
            Optional<TypeDefinition> result = typeResolver.resolveAbsoluteTypeName("java.lang." + typeName);
            if (result.isPresent()) {
                return result;
            }

            return typeResolver.resolveAbsoluteTypeName(typeName);
        }
        return findTypeDefinitionInHelper(typeName, context.getParent(), resolver);
    }

}