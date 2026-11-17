/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phoxtrot.xpath.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;

/**
 * Test class for class {@link QT3CaseRunner}.
 *
 * @author Philip Helger
 */
public final class QT3CaseRunnerTest
{
  private static final Path DIR = Path.of ("target/does-not-exist").toAbsolutePath ();

  private static QT3CaseRunner.@NonNull CaseResult _run (@NonNull final QT3CaseRunner aRunner,
                                                         @NonNull final QT3TestCase aCase,
                                                         @NonNull final List <QT3Dependency> aAllDeps)
  {
    final QT3Environment aEnv = aCase.environment () != null ? aCase.environment () : new QT3Environment ("", "", DIR);
    return aRunner.run (aCase, aAllDeps, aEnv, DIR, new QT3DocumentCache ());
  }

  private static QT3CaseRunner.@NonNull CaseResult _run (@NonNull final QT3CaseRunner aRunner,
                                                         @NonNull final QT3TestCase aCase)
  {
    return _run (aRunner, aCase, new CommonsArrayList <> ());
  }

  @Test
  public void testHarnessFailures () throws IOException
  {
    final QT3TestSet aSet = QT3CatalogReader.readTestSet ("""
        <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="s">
          <test-case name="no-assert"><test>1</test><result/></test-case>
          <test-case name="no-file"><test file="missing.xq"/><result><assert-true/></result></test-case>
        </test-set>
        """, DIR);
    final QT3CaseRunner aRunner = new QT3CaseRunner (OffsetDateTime.now ());
    // Neither is a skip any more: a broken case must be fixed or documented
    assertEquals (QT3Outcome.failInconclusive ("harness:no-assertion"),
                  _run (aRunner, aSet.testCases ().get (0)).outcome ());
    assertEquals (QT3Outcome.failInconclusive ("harness:test-file"),
                  _run (aRunner, aSet.testCases ().get (1)).outcome ());
  }

  @Test
  public void testParams () throws IOException
  {
    final QT3TestSet aSet = QT3CatalogReader.readTestSet ("""
        <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="s">
          <test-case name="as-ok">
            <environment><param name="d" as="xs:date" select="xs:date('2003-12-07')" declared="true"/></environment>
            <test>$d instance of xs:date</test><result><assert-true/></result>
          </test-case>
          <test-case name="as-mismatch">
            <environment><param name="d" as="xs:date" select="'2003-12-07'"/></environment>
            <test>1</test><result><error code="*"/></result>
          </test-case>
          <test-case name="select-error">
            <environment><param name="d" select="1 div 0"/></environment>
            <test>1</test><result><error code="*"/></result>
          </test-case>
        </test-set>
        """, DIR);
    final QT3CaseRunner aRunner = new QT3CaseRunner (OffsetDateTime.now ());
    assertTrue (_run (aRunner, aSet.testCases ().get (0)).outcome ().isPass ());
    // A param that does not match its type, or does not evaluate, must not
    // stay unbound (where an expected error would then pass)
    final QT3CaseRunner.CaseResult aMismatch = _run (aRunner, aSet.testCases ().get (1));
    assertEquals (QT3Outcome.failInconclusive ("harness:param"), aMismatch.outcome ());
    assertTrue (aMismatch.got ().contains ("does not match"));
    assertEquals (QT3Outcome.failInconclusive ("harness:param"), _run (aRunner, aSet.testCases ().get (2)).outcome ());
  }

  @Test
  public void testParamDeclaredTypeUnderStaticTyping () throws IOException
  {
    final QT3TestSet aSet = QT3CatalogReader.readTestSet ("""
        <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="s">
          <dependency type="feature" value="staticTyping"/>
          <test-case name="empty-declared">
            <environment><param name="p" as="xs:integer*" select="()"/></environment>
            <test>$p</test><result><assert-empty/></result>
          </test-case>
          <test-case name="empty-undeclared">
            <environment><param name="p" select="()"/></environment>
            <test>$p</test><result><error code="XPST0005"/></result>
          </test-case>
          <test-case name="wider-declared">
            <environment><param name="p" as="item()*" select="1"/></environment>
            <test>$p + 1</test><result><error code="XPTY0004"/></result>
          </test-case>
          <test-case name="wider-undeclared">
            <environment><param name="p" select="1"/></environment>
            <test>$p + 1</test><result><assert-eq>2</assert-eq></result>
          </test-case>
        </test-set>
        """, DIR);
    final QT3CaseRunner aRunner = new QT3CaseRunner (OffsetDateTime.now (), true);
    // The declared type is the static type: an empty value is no XPST0005
    // and a wider type than the value's raises the static type error
    for (final QT3TestCase aCase : aSet.testCases ())
      assertEquals (aCase.name (), QT3Outcome.pass (), _run (aRunner, aCase, aSet.dependencies ()).outcome ());
  }

  @Test
  public void testStrictCode () throws IOException
  {
    final QT3TestSet aSet = QT3CatalogReader.readTestSet ("""
        <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="s">
          <test-case name="wrong-code"><test>1 div 0</test><result><error code="XPTY0004"/></result></test-case>
          <test-case name="right-code"><test>1 div 0</test><result><error code="FOAR0001"/></result></test-case>
        </test-set>
        """, DIR);
    assertEquals (QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE),
                  _run (new QT3CaseRunner (OffsetDateTime.now ()), aSet.testCases ().get (0)).outcome ());
    final QT3CaseRunner aStrict = new QT3CaseRunner (OffsetDateTime.now (), true);
    assertEquals (QT3Outcome.fail (QT3Outcome.NOTE_ERROR_CODE), _run (aStrict, aSet.testCases ().get (0)).outcome ());
    assertTrue (_run (aStrict, aSet.testCases ().get (1)).outcome ().isPass ());
  }
}
