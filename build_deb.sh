#!/bin/bash
set -e

echo "=========================================="
echo " Building Debian Package: ubuntu-mic-fixer"
echo "=========================================="

PACKAGE_NAME="ubuntu-mic-fixer"
VERSION="1.0.0"
BUILD_DIR="build_deb_pkg"

# Clean previous build
rm -rf "$BUILD_DIR" "${PACKAGE_NAME}.deb"

# Create directory layout
echo "Creating package directory structure..."
mkdir -p "$BUILD_DIR/DEBIAN"
mkdir -p "$BUILD_DIR/usr/bin"
mkdir -p "$BUILD_DIR/usr/lib/$PACKAGE_NAME"
mkdir -p "$BUILD_DIR/usr/lib/systemd/user"
mkdir -p "$BUILD_DIR/usr/share/applications"
mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/scalable/apps"

# Copy debian control file
echo "Copying control file..."
cp debian/control "$BUILD_DIR/DEBIAN/control"

# Copy application source files
echo "Installing application files..."
cp -r src/* "$BUILD_DIR/usr/lib/$PACKAGE_NAME/"

# Create executable wrapper in /usr/bin/ubuntu-mic-fixer
echo "Creating launcher binary /usr/bin/ubuntu-mic-fixer..."
cat << 'EOF' > "$BUILD_DIR/usr/bin/ubuntu-mic-fixer"
#!/bin/bash
export PYTHONPATH="/usr/lib/ubuntu-mic-fixer:${PYTHONPATH}"
exec python3 /usr/lib/ubuntu-mic-fixer/main.py "$@"
EOF
chmod +x "$BUILD_DIR/usr/bin/ubuntu-mic-fixer"

# Copy Systemd user service
echo "Installing systemd user unit..."
cp systemd/ubuntu-mic-fixer.service "$BUILD_DIR/usr/lib/systemd/user/"

# Copy Desktop entry and Icon
echo "Installing desktop entry and icons..."
cp assets/ubuntu-mic-fixer.desktop "$BUILD_DIR/usr/share/applications/"
cp assets/ubuntu-mic-fixer.svg "$BUILD_DIR/usr/share/icons/hicolor/scalable/apps/ubuntu-mic-fixer.svg"

# Build debian package with dpkg-deb
echo "Building .deb package..."
dpkg-deb --build --root-owner-group "$BUILD_DIR" "${PACKAGE_NAME}.deb"

echo "=========================================="
echo " Package successfully built: ${PACKAGE_NAME}.deb"
echo "=========================================="
