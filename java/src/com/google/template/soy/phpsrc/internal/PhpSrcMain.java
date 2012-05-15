package com.google.template.soy.phpsrc.internal;

import javax.annotation.Nullable;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor.EncounteredPluralSelectMsgException;
import com.google.template.soy.phpsrc.SoyPhpSrcOptions;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;

public class PhpSrcMain {


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** The instanceof of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** Provider for getting an instance of OptimizeBidiCodeGenVisitor. */
  private final Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider;

  /** Provider for getting an instance of GenPhpCodeVisitor. */
  private final Provider<GenPhpCodeVisitor> genPhpCodeVisitorProvider;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param optimizeBidiCodeGenVisitorProvider Provider for getting an instance of
   *     OptimizeBidiCodeGenVisitor.
   * @param genPhpCodeVisitorProvider Provider for getting an instance of GenPhpCodeVisitor.
   */
  @Inject
  public PhpSrcMain(
      @ApiCall GuiceSimpleScope apiCallScope, SimplifyVisitor simplifyVisitor,
      Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider,
      Provider<GenPhpCodeVisitor> genPhpCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.simplifyVisitor = simplifyVisitor;
    this.optimizeBidiCodeGenVisitorProvider = optimizeBidiCodeGenVisitorProvider;
    this.genPhpCodeVisitorProvider = genPhpCodeVisitorProvider;
  }


  /**
   * Generates PHP source code given a Soy parse tree, an options object, and an optional bundle of
   * translated messages.
   *
   * @param soyTree The Soy parse tree to generate PHP source code for.
   * @param phpSrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return A list of strings where each string represents the PHP source code that belongs in one
   *     PHP file. The generated PHP files correspond one-to-one to the original Soy source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public String genPhpSrc(
      SoyFileSetNode soyTree, SoyPhpSrcOptions phpSrcOptions, @Nullable SoyMsgBundle msgBundle)
      throws SoySyntaxException {

    try {
      (new InsertMsgsVisitor(msgBundle, false)).exec(soyTree);
    } catch (EncounteredPluralSelectMsgException e) {
      throw new SoySyntaxException("PHPSrc backend doesn't support plural/select messages.");
    }

    apiCallScope.enter();
    try {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyPhpSrcOptions.class, phpSrcOptions);
      BidiGlobalDir bidiGlobalDir =
          SoyBidiUtils.decodeBidiGlobalDir(phpSrcOptions.getBidiGlobalDir());
      ApiCallScopeUtils.seedSharedParams(apiCallScope, msgBundle, bidiGlobalDir);

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);
      simplifyVisitor.exec(soyTree);
      return genPhpCodeVisitorProvider.get().exec(soyTree);

    } finally {
      apiCallScope.exit();
    }
  }

}
