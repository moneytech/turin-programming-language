package me.tomassetti.turin.parser.ast;

import me.tomassetti.turin.jvm.JvmMethodDefinition;
import me.tomassetti.turin.jvm.JvmNameUtils;
import me.tomassetti.turin.jvm.JvmType;
import me.tomassetti.turin.parser.analysis.resolvers.Resolver;
import me.tomassetti.turin.parser.ast.statements.Statement;
import me.tomassetti.turin.parser.ast.typeusage.FunctionReferenceTypeUsage;
import me.tomassetti.turin.parser.ast.typeusage.TypeUsage;

import java.util.List;
import java.util.stream.Collectors;

public class FunctionDefinition extends InvokableDefinition implements Named {

    public FunctionDefinition(String name, TypeUsage returnType, List<FormalParameter> parameters, Statement body) {
        super(parameters, body, name, returnType);
        this.returnType.parent = this;
        this.parameters.forEach((p) -> p.parent = FunctionDefinition.this );
        this.body.parent = this;
    }

    @Override
    public TypeUsage calcType(Resolver resolver) {
        FunctionReferenceTypeUsage functionReferenceTypeUsage = new FunctionReferenceTypeUsage(parameters.stream().map((fp)->fp.getType()).collect(Collectors.toList()), returnType);
        functionReferenceTypeUsage.setParent(this);
        return functionReferenceTypeUsage;
    }

    public JvmMethodDefinition jvmMethodDefinition(Resolver resolver) {
        String qName = this.contextName() + ".Function_" + name;
        String descriptor = "(" + String.join("", parameters.stream().map((fp)->fp.getType().jvmType(resolver).getDescriptor()).collect(Collectors.toList())) + ")" + returnType.jvmType(resolver).getDescriptor();
        return new JvmMethodDefinition(JvmNameUtils.canonicalToInternal(qName), "invoke", descriptor, true);
    }

    public boolean match(List<JvmType> argsTypes, Resolver resolver) {
        if (argsTypes.size() != parameters.size()) {
            return false;
        }
        int i = 0;
        for (FormalParameter formalParameter : parameters) {
            if (!formalParameter.getType().jvmType(resolver).isAssignableBy(argsTypes.get(i))) {
                return false;
            }
            i++;
        }
        return true;
    }
}
