#!/bin/bash
# Release script for TiviSync
VERSION=${1}

if [ -z "$VERSION" ]; then
    echo "Usage: ./release.sh <version>"
    echo "Example: ./release.sh 1.0.0"
    exit 1
fi

echo "🚀 Starting TiviSync release process for version: $VERSION"
echo "========================================================="

GIT_SUCCESS=true
GITHUB_RELEASE_SUCCESS=false

GH_CLI_AVAILABLE=false
if command -v gh &> /dev/null; then
    echo "✅ GitHub CLI found - will create GitHub release"
    GH_CLI_AVAILABLE=true
else
    echo "⚠️  GitHub CLI not found - will skip GitHub release creation"
fi

echo ""
echo "📝 Step 1: Git operations"
echo "-------------------------------------------------"

if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "⚠️  Not in a git repository - skipping git operations"
    GIT_SUCCESS=false
else
    echo "🔄 Fetching latest changes from GitHub..."
    if ! git fetch origin; then
        echo "⚠️  Failed to fetch - continuing with local changes"
        GIT_SUCCESS=false
    else
        BRANCH=$(git rev-parse --abbrev-ref HEAD)
        echo "Current branch: $BRANCH"

        if ! git merge origin/$BRANCH --no-edit; then
            echo "⚠️  Merge conflicts detected - auto-resolving..."

            if git status --porcelain | grep -q "README.md"; then
                git checkout --ours README.md
                git add README.md
            fi
            if git status --porcelain | grep -q "VERSION"; then
                echo "$VERSION" > VERSION
                git add VERSION
            fi
            if git status --porcelain | grep -q "^UU\|^AA\|^DD"; then
                echo "⚠️  Unresolved conflicts - skipping git operations"
                GIT_SUCCESS=false
            else
                git commit --no-edit -m "Auto-resolved merge conflicts for release $VERSION"
            fi
        fi
    fi
fi

echo "$VERSION" > VERSION
if [ -f "app.py" ]; then
    sed -i "s/__version__ = \".*\"/__version__ = \"$VERSION\"/" app.py
fi

generate_release_notes() {
    local version=$1
    local release_notes=""

    if [ -f "CHANGELOG.md" ]; then
        release_notes=$(awk "
        /## \[?v?${version//./\\.}\]?( -|$)/ { found=1; next }
        found && /## \[?v?[0-9]/ && !/## \[?v?${version//./\\.}\]?/ { exit }
        found { print }" CHANGELOG.md | sed '/^[[:space:]]*$/d')
    fi

    if [ -z "$release_notes" ] && [ "$GIT_SUCCESS" = true ]; then
        PREV_TAG=$(git describe --tags --abbrev=0 2>/dev/null | head -1)
        if [ -n "$PREV_TAG" ]; then
            COMMITS=$(git log --oneline --pretty=format:"- %s" "$PREV_TAG..HEAD" 2>/dev/null)
        else
            COMMITS=$(git log --oneline --pretty=format:"- %s" -10 2>/dev/null)
        fi
        if [ -n "$COMMITS" ]; then
            release_notes="### 🆕 Changes in this release:
$COMMITS"
        fi
    fi

    echo "## TiviSync v$version

📺 **Automatic TiViMate backup sync for Android TV devices**

$release_notes

### 🐳 Docker Images
- \`docker pull vansmak/tivisync:$version\`
- \`docker pull vansmak/tivisync:latest\`

### 🔧 Supported Platforms
- linux/amd64
- linux/arm64
- linux/arm/v7

### 📦 Server Installation
\`\`\`bash
docker run -d \\
  --name tivisync \\
  --restart unless-stopped \\
  -p 5005:5005 \\
  -v /path/to/your/backups:/backups:ro \\
  vansmak/tivisync:$version
\`\`\`

### 📱 Android APK
Download from the Actions tab > latest successful build > Artifacts.
Sideload via Downloader or CX File Explorer on your Android TV device.

### ✨ Features
- One-tap TiViMate backup sync across all your Android TV devices
- Automatically detects and downloads newer backups
- Silent when already up to date
- Local network only — your data never leaves your home"
}

if [ "$GIT_SUCCESS" = true ]; then
    git add .
    git add -A

    if ! git diff --staged --quiet; then
        if git commit -m "TiviSync v$VERSION"; then
            echo "✅ Commit created"
        else
            GIT_SUCCESS=false
        fi
    fi

    if [ "$GIT_SUCCESS" = true ]; then
        if git tag -a "v$VERSION" -m "TiviSync v$VERSION"; then
            echo "✅ Tag created"
        else
            GIT_SUCCESS=false
        fi
    fi

    if [ "$GIT_SUCCESS" = true ]; then
        if git push origin $BRANCH && git push origin "v$VERSION"; then
            echo "✅ Pushed to GitHub"
        else
            echo "⚠️  Failed to push - continuing with Docker build"
            GIT_SUCCESS=false
        fi
    fi
fi

if [ "$GIT_SUCCESS" = true ] && [ "$GH_CLI_AVAILABLE" = true ]; then
    echo ""
    echo "📋 Step 2: Creating GitHub Release"
    if gh auth status &> /dev/null; then
        RELEASE_NOTES=$(generate_release_notes "$VERSION")
        if echo "$RELEASE_NOTES" | gh release create "v$VERSION" \
            --title "TiviSync v$VERSION" \
            --notes-file - \
            --latest; then
            echo "✅ GitHub release created!"
            GITHUB_RELEASE_SUCCESS=true
        fi
    else
        echo "⚠️  GitHub CLI not authenticated - run 'gh auth login'"
    fi
fi

echo ""
echo "🐳 Step 3: Docker build and push"
echo "------------------------------------------------------"

BUILDER_NAME="tivisync-builder-$$"

cleanup_buildx() {
    echo "🧹 Cleaning up buildx..."
    docker buildx rm $BUILDER_NAME 2>/dev/null || true
    docker container prune -f --filter "label=com.docker.compose.project=buildx" 2>/dev/null || true
    docker ps -aq --filter "name=builder" | xargs -r docker rm -f 2>/dev/null || true
    docker buildx prune -f 2>/dev/null || true
    echo "✅ Cleanup done!"
}

trap cleanup_buildx EXIT INT TERM

docker buildx create --name $BUILDER_NAME --use || exit 1
docker buildx inspect $BUILDER_NAME --bootstrap || exit 1

echo "🏗️  Building TiviSync multi-arch image v$VERSION..."

if docker buildx build \
  --builder $BUILDER_NAME \
  --platform linux/amd64,linux/arm64,linux/arm/v7 \
  -t vansmak/tivisync:$VERSION \
  -t vansmak/tivisync:latest \
  --push \
  .; then
    echo "✅ Docker images pushed: vansmak/tivisync:$VERSION and :latest"
else
    echo "❌ Docker build failed!"
    exit 1
fi

echo ""
echo "🎉 TiviSync v$VERSION release completed!"
echo "  GitHub:     https://github.com/vansmak/tivisync"
echo "  Docker Hub: https://hub.docker.com/r/vansmak/tivisync"
echo "  APK:        GitHub Actions > latest build > Artifacts"
