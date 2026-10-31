#!/usr/bin/env bash
# Dicio güncellemelerini birleştirme rutini (PLAN Faz 7). Kullanım: bash tool/dicio_guncelle.sh [--birlestir]
#
# 1. upstream'i (DicioTeam/dicio-android, dal main) getirir, bizde olmayan commit'leri listeler.
# 2. --birlestir verilirse `dicio-guncelleme` dalında birleştirir; çakışan dosyaları ve içlerindeki
#    `// ASISTAN:` kancalarını gösterir (çakışma çoğunlukla kancalı Dicio dosyalarında çıkar).
# 3. Sonra elle: çakışmaları çöz → asistan testleri + release derlemesi → main'e al.
set -euo pipefail
cd "$(dirname "$0")/.."
git remote get-url upstream >/dev/null 2>&1 || git remote add upstream https://github.com/DicioTeam/dicio-android
git fetch upstream main
YENI=$(git rev-list --count HEAD..upstream/main)
echo "Dicio'da bizde olmayan commit: $YENI"
[ "$YENI" = 0 ] && exit 0
git log --oneline HEAD..upstream/main
echo
echo "Kancalı Dicio dosyaları (birleştirmede gözden geçirilecek):"
grep -rl "// ASISTAN" app/src/main/kotlin/org app/src/main/proto app/src/main/res 2>/dev/null | sort || true
[ "${1:-}" = "--birlestir" ] || { echo; echo "Birleştirmek için: bash tool/dicio_guncelle.sh --birlestir"; exit 0; }
git switch -c dicio-guncelleme
if git merge --no-ff upstream/main -m "Dicio güncellemesi: upstream/main birleştirildi"; then
  echo "Çakışma yok."
else
  echo "Çakışan dosyalar:"; git diff --name-only --diff-filter=U
fi
echo
echo "Sonra: ./gradlew :app:testDebugUnitTest --tests 'com.ses.asistan.*' && ./gradlew :app:assembleRelease"
echo "Yeşilse: git switch main && git merge dicio-guncelleme && git push origin main"
