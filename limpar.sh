#!/bin/bash
# Script para limpar todos os arquivos .class e diretorios de build do projeto E.D.E.N.

echo "Limpando arquivos compilados (.class)..."
find . -name "*.class" -type f -delete 2>/dev/null
rm -rf Cliente/bin Servidor/bin 2>/dev/null

echo "Concluido! Todos os arquivos .class foram removidos com sucesso."

