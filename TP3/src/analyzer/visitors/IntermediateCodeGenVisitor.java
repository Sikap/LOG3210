package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;

import javax.xml.crypto.Data;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Objects;
import java.util.Vector;


/**
 * Created: 19-02-15
 * Last Changed: 20-10-6
 * Author: Félix Brunet & Doriane Olewicki
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class IntermediateCodeGenVisitor implements ParserVisitor {

    //le writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter writer;

    public IntermediateCodeGenVisitor(PrintWriter writer) {
        this.writer = writer;
    }
    public HashMap<String, VarType> SymbolTable = new HashMap<>();

    private int id = 0;
    private int label = 0;
    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genId() {
        return "_t" + id++;
    }

    //génère un nouveau Label qu'il est possible de print.
    private String genLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        String label = genLabel();
        node.childrenAccept(this, label);
        this.writer.println(label);
        return null;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        VarType t;
        if(node.getValue().equals("bool")) {
            t = VarType.Bool;
        } else {
            t = VarType.Number;
        }
        SymbolTable.put(id.getValue(), t);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        String endLabel =  data.toString();
        for(int i=0; i < node.jjtGetNumChildren(); i++){
            if(i == node.jjtGetNumChildren() - 1){
                node.jjtGetChild(i).jjtAccept(this, endLabel);
            }else{
                String beginStatement = genLabel();
                node.jjtGetChild(i).jjtAccept(this, beginStatement);
                this.writer.println(beginStatement);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        for(int i=0; i< node.jjtGetNumChildren(); i++){
            node.jjtGetChild(i).jjtAccept(this,data);
        }
        return null;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        String AssgnLabel = genLabel();
        node.jjtGetChild(0).jjtAccept(this, AssgnLabel);
        this.writer.println(AssgnLabel);
        BoolLabel boolLabel = new BoolLabel(genLabel(), (String) data);
        node.jjtGetChild(1).jjtAccept(this, boolLabel);
        this.writer.println(boolLabel.lTrue);
        node.jjtGetChild(3).jjtAccept(this, AssgnLabel);
        node.jjtGetChild(2).jjtAccept(this, AssgnLabel);
        this.writer.println("goto " + AssgnLabel);
        return null;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        if(node.jjtGetNumChildren() == 2){
            String label = genLabel();
            BoolLabel boolLabel = new BoolLabel(label, data.toString());
            node.jjtGetChild(0).jjtAccept(this, boolLabel);
            this.writer.println(boolLabel.lTrue);
            node.jjtGetChild(1).jjtAccept(this, data);
        }
        else {
            String label1 = genLabel();
            String label2 = genLabel();
            BoolLabel boolLabel = new BoolLabel(label1, label2);
            node.jjtGetChild(0).jjtAccept(this, boolLabel);
            this.writer.println(boolLabel.lTrue);
            node.jjtGetChild(1).jjtAccept(this, data);
            this.writer.println("goto " + data);
            this.writer.println(boolLabel.lFalse);
            node.jjtGetChild(2).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String begin = genLabel();
        BoolLabel boolLabel = new BoolLabel(genLabel(), data.toString());
        this.writer.println(begin);
        //Visite de l'expr booleene B
        node.jjtGetChild(0).jjtAccept(this, boolLabel);
        this.writer.println(boolLabel.lTrue);
        //Visite du stmt ou block S1
        node.jjtGetChild(1).jjtAccept(this, begin);
        this.writer.println("goto " + begin);
        return null;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {

        String identifierName = ((ASTIdentifier)node.jjtGetChild(0)).getValue();

        VarType type = SymbolTable.get(identifierName);
        if(type == VarType.Number){
            String exp = (String) node.jjtGetChild(1).jjtAccept(this, data);
            writer.println(identifierName +" = "+ exp);
        }else{
            String labelTrue = genLabel();
            String labelFalse = genLabel();
            BoolLabel b = new BoolLabel(labelTrue, labelFalse);
            node.jjtGetChild(1).jjtAccept(this, b);
            writer.println(b.lTrue);
            writer.println(identifierName +" = 1");
            writer.println("goto " + (String) data);
            writer.println(b.lFalse);
            writer.println(identifierName +" = 0");
        }
        return null;
    }


    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    //Expression arithmétique
    /*
    Les expressions arithmétique add et mult fonctionne exactement de la même manière. c'est pourquoi
    il est plus simple de remplir cette fonction une fois pour avoir le résultat pour les deux noeuds.

    On peut bouclé sur "ops" ou sur node.jjtGetNumChildren(),
    la taille de ops sera toujours 1 de moins que la taille de jjtGetNumChildren
     */
    public Object generateCodeForAddAndMultiplication(SimpleNode node, Object data, Vector<String> ops) {
        String d = genId();
        String tmp;
        StringBuilder addr = new StringBuilder();
        for(int i=0 ; i < node.jjtGetNumChildren(); i++){
            tmp = (String) node.jjtGetChild(i).jjtAccept(this, data);
            if(tmp != null)
                addr.append(tmp);
            if(i < ops.size())
                addr.append(" ").append(ops.elementAt(i)).append(" ");
        }
        writer.println(d + " = " + addr);
        return d;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        String exp = "";
        if(node.getOps().size() > 0){
            exp = (String) generateCodeForAddAndMultiplication(node, data, node.getOps());
        }else {
            exp = (String) node.jjtGetChild(0).jjtAccept(this, data);
        }
        return exp;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        String exp = "";
        if(node.getOps().size() > 0){
            exp = (String) generateCodeForAddAndMultiplication(node, data, node.getOps());
        }else {
            exp = (String) node.jjtGetChild(0).jjtAccept(this, data);
        }
        return exp;
    }

    //UnaExpr est presque pareil au deux précédente. la plus grosse différence est qu'il ne va pas
    //chercher un deuxième noeud enfant pour avoir une valeur puisqu'il s'agit d'une opération unaire.
    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        String exp = "";
        if(node.getOps().size() > 0){
            exp = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String tmp;
            for(int i =0; i < node.getOps().size(); i++){
                tmp = genId();
                writer.println(tmp + " = " + node.getOps().elementAt(0) + " " + exp);
                exp = tmp;
            }
        }else exp = (String) node.jjtGetChild(0).jjtAccept(this, data);
        return exp;
    }

    //expression logique
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        if(!node.getOps().isEmpty()){
            String op = node.getOps().elementAt(0).toString();
            BoolLabel dataBl = (BoolLabel) data;
            //B1
            BoolLabel firstBoolLabel = new BoolLabel("", "");
            //B2
            BoolLabel secondBool = new BoolLabel("", "");
            if(op.equals("&&")){
                firstBoolLabel.lTrue = genLabel();
                firstBoolLabel.lFalse = dataBl.lFalse;
                secondBool.lTrue = dataBl.lTrue;
                secondBool.lFalse = dataBl.lFalse;
                node.jjtGetChild(0).jjtAccept(this, firstBoolLabel);
                this.writer.println(firstBoolLabel.lTrue);
                node.jjtGetChild(1).jjtAccept(this, secondBool);
            } else if(op.equals("||")){
                firstBoolLabel.lTrue = dataBl.lTrue;
                firstBoolLabel.lFalse = genLabel();
                secondBool.lTrue = dataBl.lTrue;
                secondBool.lFalse = dataBl.lFalse;
                node.jjtGetChild(0).jjtAccept(this, firstBoolLabel);
                this.writer.println(firstBoolLabel.lFalse);
                node.jjtGetChild(1).jjtAccept(this, secondBool);
            }
            return null;
        }
        return node.jjtGetChild(0).jjtAccept(this, data);
    }



    @Override
    public Object visit(ASTCompExpr node, Object data) {
        if(node.jjtGetNumChildren() > 1) {
            BoolLabel boolLabel = (BoolLabel) data;
            String firstExpr = (String) node.jjtGetChild(0).jjtAccept(this, null);
            String secondExpr = (String) node.jjtGetChild(1).jjtAccept(this, null);
            this.writer.println("if " + firstExpr + " " + node.getValue() + " " + secondExpr + " goto " + boolLabel.lTrue);
            this.writer.println("goto " + boolLabel.lFalse);
            return node.getValue();
        }
        return  (String)  node.jjtGetChild(0).jjtAccept(this, data);
    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        String exp = "";
        if((node.getOps().size() % 2) != 0){
            BoolLabel b = (BoolLabel) data;
            BoolLabel firstBool = new BoolLabel(b.lFalse, b.lTrue);
            return node.jjtGetChild(0).jjtAccept(this, firstBool);
        }else {
            exp = (String) node.jjtGetChild(0).jjtAccept(this, data);
        }
        return exp;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    /*
    BoolValue ne peut pas simplement retourné sa valeur à son parent contrairement à GenValue et IntValue,
    Il doit plutôt généré des Goto direct, selon sa valeur.
     */
    @Override
    public Object visit(ASTBoolValue node, Object data) {
        String boolVal = Integer.toString(node.getValue() ? 1 : 0);
        BoolLabel b = (BoolLabel) data;
        if(node.getValue())
            writer.println("goto " + b.lTrue);
        else
            writer.println("goto " + b.lFalse);
        return boolVal;
    }

    /*
    si le type de la variable est booléenne, il faudra généré des goto ici.
    le truc est de faire un "if value == 1 goto Label".
    en effet, la structure "if valeurBool goto Label" n'existe pas dans la syntaxe du code à trois adresse.
     */
    @Override
    public Object visit(ASTIdentifier node, Object data) {
        String  idName = "";
        if(SymbolTable.get(node.getValue()) == VarType.Number){
            idName = node.getValue();
        }else {
            BoolLabel b = (BoolLabel) data;
            idName = node.getValue();
            writer.println("if " + idName + " == 1 goto " + b.lTrue);
            writer.println("goto " + b.lFalse);
        }
        return idName;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class BoolLabel {
        public String lTrue;
        public String lFalse;

        public BoolLabel(String t, String f) {
            lTrue = t;
            lFalse = f;
        }
    }


}
