package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import javax.lang.model.element.VariableElement;
import javax.xml.crypto.Data;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created: 19-01-10
 * Last Changed: 21-09-28
 * Author: Esther Guerrier
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreur lorqu'une erreur sémantique est détecté.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter writer;

    private HashMap<String, VarType> symbolTable = new HashMap<>(); // mapping variable -> type

    // variable pour les metrics
    private int VAR = 0;
    private int WHILE = 0;
    private int IF = 0;
    private int FOR = 0;
    private int OP = 0;

    public SemantiqueVisitor(PrintWriter writer) {
        this.writer = writer;
    }

    /*
    Le Visiteur doit lancer des erreurs lorsqu'un situation arrive.

    regardez l'énoncé ou les tests pour voir le message à afficher et dans quelle situation.
    Lorsque vous voulez afficher une erreur, utilisez la méthode print implémentée ci-dessous.
    Tous vos tests doivent passer!!

     */

    @Override
    public Object visit(SimpleNode node, Object data) {
//        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        try{
            node.childrenAccept(this, data);
        }catch(SemantiqueError e){
            throw e;
        }
        finally {
            writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, FOR:%d, OP:%d}", VAR, WHILE, IF, FOR, OP));
        }
        return null;
    }

    /*
    Ici se retrouve les noeuds servant à déclarer une variable.
    Certaines doivent enregistrer les variables avec leur type dans la table symbolique.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        node.childrenAccept(this, data);
        VAR++;
        return null;
    }

    @Override
    public Object visit(ASTNormalDeclaration node, Object data) throws SemantiqueError {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if(symbolTable.containsKey(varName)){
            throw new SemantiqueError(String.format("Invalid declaration... variable "+ varName + " already exists"));
        }else {
            symbolTable.put(varName, node.getValue().equals("num") ? VarType.num : VarType.bool);
        }
        return null;
    }

    @Override
    public Object visit(ASTListDeclaration node, Object data) throws SemantiqueError {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if(symbolTable.containsKey(varName)){
            throw new SemantiqueError(String.format("Invalid declaration... variable "+ varName + " already exists"));
        }else {
            symbolTable.put(varName, node.getValue().equals("listnum") ? VarType.listnum : VarType.listbool);
            node.childrenAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }


    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    /*
     * Il faut vérifier que le type déclaré à gauche soit compatible avec la liste utilisée à droite. N'oubliez pas
     * de vérifier que les variables existent.
     */

    @Override
    public Object visit(ASTForEachStmt node, Object data) throws SemantiqueError {
        String identifierName = ((ASTIdentifier) node.jjtGetChild(1)).getValue();
        String normalDeclarationType = ((ASTNormalDeclaration) node.jjtGetChild(0)).getValue();
        if(symbolTable.containsKey(identifierName)){
            String identifierType = symbolTable.get(identifierName).name();
            VAR++;
            if(identifierType.equals("listnum") || identifierType.equals("listbool")){
                if((normalDeclarationType.equals("num") && identifierType.equals("listnum")) || (normalDeclarationType.equals("bool") && identifierType.equals("listbool"))){
                    node.childrenAccept(this, data);
                    FOR++;
                }else {
                    throw new SemantiqueError(String.format("Array type " + identifierType + " is incompatible with declared variable of type " + normalDeclarationType +"..."));
                }
            }else{
                throw new SemantiqueError("Array type is required here...");
            }
        }else {
            throw new SemantiqueError(String.format("Invalid use of undefined Identifier "+ identifierName));
        }
        return null;
    }

    /*
    Ici faites attention!! Lisez la grammaire, c'est votre meilleur ami :)
     */
    @Override
    public Object visit(ASTForStmt node, Object data) {
        DataStruct varData = new DataStruct(VarType.bool);
        node.childrenAccept(this, varData);
        FOR++;
        return null;
    }

    /*
    Méthode recommandée à implémenter puisque vous remarquerez que quelques fonctions ont exactement le même code! N'oubliez
    -pas que la qualité du code est évalué :)
     */
    private void callChildenCond(SimpleNode node) {

    }

    /*
    les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    On doit aussi compter les conditions dans les variables IF et WHILE
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        DataStruct varData = new DataStruct(VarType.bool);
        node.childrenAccept(this, varData);
        if(varData.type != VarType.bool)
            throw new SemantiqueError("Invalid type in condition");
        IF++;
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) throws SemantiqueError {
        DataStruct varData = new DataStruct(VarType.bool);
        node.childrenAccept(this, varData);
        if(varData.type != VarType.bool)
            throw new SemantiqueError("Invalid type in condition");

        WHILE++;
        return null;
    }

    /*
    On doit vérifier que le type de la variable est compatible avec celui de l'expression.
    La variable doit etre déclarée.
     */
    @Override
    public Object visit(ASTAssignStmt node, Object data) throws SemantiqueError {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
//        SimpleNode normalDeclarationType = ((ASTExpr) node.jjtGetChild(1));
        if(symbolTable.containsKey(varName)){
            DataStruct varData = new DataStruct(symbolTable.get(varName));
            node.childrenAccept(this, varData);
            if(symbolTable.get(varName) != varData.type)
                throw new SemantiqueError(String.format("Invalid type in assignation of Identifier "+ varName +"... was expecting "+ symbolTable.get(varName) + " but got "+ varData.type));

        }else {
            throw new SemantiqueError(String.format("Invalid use of undefined Identifier "+ varName));
        }
        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        //Il est normal que tous les noeuds jusqu'à expr retourne un type.
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        /*attention, ce noeud est plus complexe que les autres.
        si il n'a qu'un seul enfant, le noeud a pour type le type de son enfant.

        si il a plus d'un enfant, alors ils s'agit d'une comparaison. il a donc pour type "Bool".

        de plus, il n'est pas acceptable de faire des comparaisons de booleen avec les opérateur < > <= >=.
        les opérateurs == et != peuvent être utilisé pour les nombres et les booléens, mais il faut que le type soit le même
        des deux côté de l'égalité/l'inégalité.
        */

        ArrayList<VarType> childrenTypes = new ArrayList<>();
        if(node.jjtGetNumChildren() > 1){
            OP+= node.jjtGetNumChildren()-1;
            callChildren(node, data, VarType.bool);
            ((DataStruct)data).type = VarType.bool;
        }else{
            node.childrenAccept(this, data);
        }

        return null;
    }

    private void callChildren(SimpleNode node, Object data, VarType validType) {
        for(int i = 0; i < node.jjtGetNumChildren(); i++){
            Object value = ((SimpleNode) node.jjtGetChild(i)).childrenAccept(this, data);
//            if(symbolTable.get(data) != validType)
//                throw new SemantiqueError(String.format("Invalid type for Identifier"));
        }
    }

    /*
    opérateur binaire
    si il n'y a qu'un enfant, aucune vérification à faire.
    par exemple, un AddExpr peut retourné le type "Bool" à condition de n'avoir qu'un seul enfant.
     */
    @Override
    public Object visit(ASTAddExpr node, Object data) {
        if(!node.getOps().isEmpty()) {
            OP += node.getOps().size();
        }
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        if(!node.getOps().isEmpty()) {
            OP += node.getOps().size();
        }
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        if(!node.getOps().isEmpty()) {
            OP += node.getOps().size();
            ((DataStruct)data).type = VarType.bool;
        }
        node.childrenAccept(this, data);
        return null;
    }

    /*
    opérateur unaire
    les opérateur unaire ont toujours un seul enfant.

    Cependant, ASTNotExpr et ASTUnaExpr ont la fonction "getOps()" qui retourne un vecteur contenant l'image (représentation str)
    de chaque token associé au noeud.

    Il est utile de vérifier la longueur de ce vecteur pour savoir si une opérande est présente.

    si il n'y a pas d'opérande, ne rien faire.
    si il y a une (ou plus) opérande, ils faut vérifier le type.

    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if(!node.getOps().isEmpty()) {
            OP += node.getOps().size();
        }
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        if(!node.getOps().isEmpty()) {
            OP += node.getOps().size();
        }
        node.childrenAccept(this, data);
        return null;
    }

    /*
    les noeud ASTIdentifier aillant comme parent "GenValue" doivent vérifier leur type et vérifier leur existence.

    Ont peut envoyé une information a un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }


    @Override
    public Object visit(ASTBoolValue node, Object data) throws SemantiqueError {
            node.childrenAccept(this, ((DataStruct)data).type = VarType.bool);
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if(node.jjtGetParent().getClass() == ASTGenValue.class ){
            if(symbolTable.containsKey(node.getValue())){
                ((DataStruct)data).type = symbolTable.get(node.getValue());
                node.childrenAccept(this, data);
            }else{
                throw new SemantiqueError(String.format("Invalid use of undefined Identifier "+ node.getValue()));
            }
        }else{
            node.childrenAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) throws SemantiqueError {
            node.childrenAccept(this, ((DataStruct)data).type = VarType.num);
        return null;
    }


    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        bool,
        num,
        listnum,
        listbool
    }

    private class DataStruct {
        public VarType type;
        public String varName;

        public DataStruct(VarType VarType, String varName) {}

        public DataStruct(VarType p_type) {
            type = p_type;
        }
    }
}
