////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2015 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.checks.coding;

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Restricts the number of statements per line to one.
 * <p>
 *     Rationale: It's very difficult to read multiple statements on one line.
 * </p>
 * <p>
 *     In the Java programming language, statements are the fundamental unit of
 *     execution. All statements except blocks are terminated by a semicolon.
 *     Blocks are denoted by open and close curly braces.
 * </p>
 * <p>
 *     OneStatementPerLineCheck checks the following types of statements:
 *     variable declaration statements, empty statements, assignment statements,
 *     expression statements, increment statements, object creation statements,
 *     'for loop' statements, 'break' statements, 'continue' statements,
 *     'return' statements, import statements.
 * </p>
 * <p>
 *     The following examples will be flagged as a violation:
 * </p>
 * <pre>
 *     //Each line causes violation:
 *     int var1; int var2;
 *     var1 = 1; var2 = 2;
 *     int var1 = 1; int var2 = 2;
 *     var1++; var2++;
 *     Object obj1 = new Object(); Object obj2 = new Object();
 *     import java.io.EOFException; import java.io.BufferedReader;
 *     ;; //two empty statements on the same line.
 *
 *     //Multi-line statements:
 *     int var1 = 1
 *     ; var2 = 2; //violation here
 *     int o = 1, p = 2,
 *     r = 5; int t; //violation here
 *     good(); for(int i = 0; i < 3; i++) { bad(); }
 *     good(); do { bad(); } while(false);
 *     good(); do { bad(); } while(false);
 *     good(); bad();
 *     cb.addActionListener((final ActionEvent e) -> { good(); }); bad();
 *     cb.addActionListener((final ActionEvent e) -> { good(); bad();
 *         });
 *     do { good(); bad(); } while(false);
 *     for(int i = 0; i < 3; i++) good(); bad();
 *     for(int i = 0; i < 3; i++) { good(); bad(); }
 *     for(int i = 0; i < 3; i++) { good(); } bad();
 * </pre>
 *
 * @author Alexander Jesse
 * @author Oliver Burn
 * @author Andrei Selkin
 * @author Richard Schulte
 */
public final class OneStatementPerLineCheck extends Check {

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_KEY = "multiple.statements.line";

    /**
     * Hold the line-number where the last statement ended.
     */
    private int lastStatementEnd = -1;

    /**
     * Lambda expression
     */
    private DetailAST lambda = null;

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[]{
            TokenTypes.SEMI,
            TokenTypes.EMPTY_STAT,
            TokenTypes.ELIST,
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return getAcceptableTokens();
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        lastStatementEnd = -1;
    }

    @Override
    public void visitToken(DetailAST ast) {
        switch (ast.getType()) {
        case TokenTypes.EMPTY_STAT: // fall through to SEMI
        case TokenTypes.SEMI:
            DetailAST currentStatement = multilineStatement(ast);
            DetailAST sibling = ast.getPreviousSibling();
            // skip FOR_ITERATOR and EXPR in 'for (;;) EXPR;', 'do EXPR; while();', and
            // 'do { EXPR; } while();'
            boolean skip = false;
            while(sibling != null && !skip && ! isSemi(sibling)) {
                skip = sibling.getType() == TokenTypes.FOR_CONDITION
                    || sibling.getType() == TokenTypes.FOR_ITERATOR
                    || sibling.getType() == TokenTypes.DO_WHILE;
                sibling = sibling.getPreviousSibling();
            }

            // skip EXPR in 'for(;;) { EXPR; }'
            if(ast.getParent() != null && ast.getParent().getType() == TokenTypes.SLIST) {
                sibling = ast.getParent().getPreviousSibling();
                while(sibling != null && !skip && ! isSemi(sibling)) {
                    skip = sibling.getType() == TokenTypes.FOR_ITERATOR;
                    sibling = sibling.getPreviousSibling();
                }

                // don't skip EXPR2 in  in 'for(;;) { EXPR1; EXPR2; }'
                sibling = ast.getPreviousSibling();
                while(sibling != null && skip) {
                    skip = ! isSemi(sibling);
                    //sibling.getType() != TokenTypes.SEMI;
                    sibling = sibling.getPreviousSibling();
                }
            }
            if(!skip) {
                if (isOnTheSameLine(currentStatement)) {
                    log(ast, MSG_KEY);
                }
            }
            break;
        default:
            break;
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        switch (ast.getType()) {
        case TokenTypes.EMPTY_STAT: // fall through to SEMI
        case TokenTypes.SEMI:
            if(ast.getPreviousSibling() != null && ast.getPreviousSibling().getType() != TokenTypes.FOR_INIT) {
                lastStatementEnd = ast.getLineNo();
            }
            lambda = null;
            break;
        case TokenTypes.ELIST:
            if(ast.getFirstChild() != null && ast.getFirstChild().getType() == TokenTypes.LAMBDA) {
                lambda = ast.getFirstChild();
            }
            break;
        default:
            break;
        }
    }

    /**
     * Checks if the statement is a SEMI or EMPTY_STAT
     * @param ast token for the current statement.
     * @return true if ast is a SEMI or EMPTY_STAT
     */
    private boolean isSemi(DetailAST ast) {
        return ast.getType() == TokenTypes.SEMI || ast.getType() == TokenTypes.EMPTY_STAT;
    }

    /**
     * Checks whether the statement is on the same line as the previous statement
     * @param ast token for the current statement.
     * @return true if the statement is on the same line as the previous statement
     */
    private boolean isOnTheSameLine(DetailAST ast) {
        return lastStatementEnd == ast.getLineNo()
            && (lambda == null || ast.getLineNo() != lambda.getLineNo());
    }

    /**
     * Returns starting node for a statement, handling multi line statements.
     * @param ast token
     * @return node where ast starts
     */
    private DetailAST multilineStatement(DetailAST ast) {
        if(ast.getPreviousSibling() != null
           && ast.getPreviousSibling().getLineNo() != ast.getLineNo()
           && ast.getParent() != null) {
            return ast.getPreviousSibling();
        }
        return ast;
    }
}
