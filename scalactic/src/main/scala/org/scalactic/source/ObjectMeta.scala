/*
 * Copyright 2001-2016 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.scalactic.source

import org.scalactic.ScalacticVersions

trait ObjectMeta {

  def fieldNames: scala.collection.immutable.IndexedSeq[String]

  def hasField(name: String): Boolean = fieldNames.contains(name)

  def value(name: String): Any

  def typeName(name: String): String

  def shortTypeName(name: String): String

}

object ObjectMeta {

  def objectMetaUsingScalaReflection(v: Any): ObjectMeta = {
    import reflect.runtime.universe._

    val typeMirror = runtimeMirror(v.getClass.getClassLoader)
    val instanceMirror = typeMirror.reflect(v)
    val symbol: ClassSymbol = instanceMirror.symbol

    def mapCaseAccessor(s: Symbol): Option[String] = {
      s match {
        case m: MethodSymbol =>
          if (m.isCaseAccessor)
            Some(s.name.toString)
          else
            None

        case m: TermSymbol if m.isVal =>
          val name = m.name.toString.trim
          if (m.name.toString.endsWith("$mcI$sp"))
            Some(name.substring(0, name.length - 7))
          else
            Some(name)

        case other =>
          None
      }
    }

    new ObjectMeta {

      val caseAccessorSymbols = symbol.toType.declarations

      lazy val fieldNames = {
        symbol.toType.declarations.flatMap(mapCaseAccessor).toVector.distinct
      }

      def value(name: String): Any = {
        caseAccessorSymbols.find(s => (s.name.toString == name || s.name.toString == (name + "$mcI$sp")) && (s.isMethod || s.isTerm)) match {
          case Some(fieldSymbol) =>
            val fieldMirror = instanceMirror.reflectField(fieldSymbol.asTerm)
            fieldMirror.get

          case None =>
            throw new IllegalArgumentException("'" + name + "' is not attribute for this instance.")
        }
      }

      def typeName(name: String): String = {
        caseAccessorSymbols.find(s => s.name.toString == name) match {
          case Some(fieldSymbol) =>
            // This is a safe cast
            fieldSymbol.asInstanceOf[MethodSymbol].returnType.typeSymbol.fullName

          case None =>
            throw new IllegalArgumentException("'" + name + "' is not attribute for this instance.")
        }
      }

      def shortTypeName(name: String): String = {
        caseAccessorSymbols.find(s => s.name.toString == name) match {
          case Some(fieldSymbol) =>
            // This is a safe cast
            fieldSymbol.asInstanceOf[MethodSymbol].returnType.typeSymbol.name.decoded

          case None =>
            throw new IllegalArgumentException("'" + name + "' is not attribute for this instance.")
        }
      }
    }
  }

  def objectMetaUsingJavaReflection(v: Any): ObjectMeta =
    new ObjectMeta {
      lazy val privFields = v.getClass.getDeclaredFields.filter(!_.isAccessible).map(_.getName)

      lazy val fieldNames = {
        v.getClass.getDeclaredMethods.filter { m =>
          m.getParameterTypes.isEmpty && privFields.contains(m.getName)
        }.map { f =>
          if (f.getName.endsWith("$mcI$sp"))
            f.getName.dropRight(7)
          else
            f.getName
        }.toVector
      }

      def value(name: String): Any =
        try {
          v.getClass.getDeclaredMethod(name).invoke(v)
        }
        catch {
          case e: NoSuchMethodException =>
            try {
              v.getClass.getDeclaredMethod(name + "$mcI$sp").invoke(v)
            }
            catch {
              case e: NoSuchMethodException =>
                throw new IllegalArgumentException("'" + name + "' is not attribute for this instance.")
            }
        }

      def typeName(name: String): String = value(name).getClass.getName

      def shortTypeName(name: String): String = value(name).getClass.getSimpleName
    }

  def apply(v: Any): ObjectMeta =
    if (ScalacticVersions.BuiltForScalaVersion == "2.10")
      objectMetaUsingJavaReflection(v)
    else
      objectMetaUsingScalaReflection(v)

}