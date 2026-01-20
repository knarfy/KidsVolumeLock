# Instrucciones para Proteger la Rama Master

Para proteger la rama `master` en GitHub y evitar cambios accidentales:

## Opción 1: Configuración Manual en GitHub (Recomendado)

1. Ve a tu repositorio: https://github.com/knarfy/KidsVolumeLock
2. Haz clic en **Settings** (Configuración)
3. En el menú lateral, haz clic en **Branches** (Ramas)
4. En "Branch protection rules", haz clic en **Add rule** (Añadir regla)
5. En "Branch name pattern", escribe: `master`
6. Activa las siguientes opciones:
   - ✅ **Require linear history** (Requerir historial lineal)
   - ✅ **Do not allow bypassing the above settings** (opcional)
   - ✅ **Include administrators** (si quieres que los admins también sigan las reglas)
7. Haz clic en **Create** o **Save changes**

## Protecciones Recomendadas

### Nivel Básico (Ya configurado arriba):
- ✅ **Require linear history**: Evita merge commits, mantiene el historial limpio
- ✅ **Block force pushes**: No se pueden hacer `git push --force`
- ✅ **Block deletions**: No se puede eliminar la rama

### Nivel Intermedio (Opcional):
Si trabajas con otros desarrolladores, también activa:
- **Require pull request reviews before merging**: Requiere PR en vez de push directo
- **Require status checks to pass**: Los builds de GitHub Actions deben pasar

### Nivel Avanzado (Opcional):
- **Require signed commits**: Solo commits firmados con GPG
- **Require deployments to succeed**: Requiere deployment exitoso antes de merge

## Verificación

Para verificar que está protegida:
```powershell
.\bin\gh.exe api repos/knarfy/KidsVolumeLock/branches/master/protection
```

## Trabajar con Protecciones

Una vez protegida la rama, para hacer cambios:

### Método 1: Trabajar en ramas (Recomendado)
```powershell
# Crear rama para cambios
git checkout -b fix/algo

# Hacer cambios y commit
git add .
git commit -m "Descripción del cambio"

# Push de la rama
git push origin fix/algo

# Crear Pull Request en GitHub
# Hacer merge del PR cuando esté listo
```

### Método 2: Commits directos (si no hay protecciones estrictas)
Si solo tienes "linear history", puedes seguir haciendo push directo:
```powershell
git add .
git commit -m "Cambio"
git push origin master
```

## Estado Actual

Los scripts de automatización (`deploy.ps1`) seguirán funcionando normalmente con estas protecciones básicas.
