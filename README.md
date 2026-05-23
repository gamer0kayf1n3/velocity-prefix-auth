# velocity-prefix-auth
## A Velocity plugin that normalizes player identities with a prefix to prevent name collisions between cracked and premium accounts.

> [!CAUTION]
> This project is in very early stages of development and is not yet ready for production use. Most functionality is not yet implemented and the API is subject to change without warning. Use at your own risk.

### Features
- Pre-auth identity normalization with a configurable prefix for cracked accounts.
- Prevention of name collisions between cracked and premium accounts.

### Installation
1. Download the latest release of the plugin from the [releases page](https://github.com/gamer0kayf1n3/velocity-prefix-auth/releases).
2. Place the downloaded JAR file into the `plugins` directory of your Velocity proxy server
3. Restart the Velocity proxy server to load the plugin.

### Configuration
The plugin will create a default configuration file in the `plugins/velocity-prefix-auth` directory. You can edit this file to customize the prefix used for cracked accounts and other settings.

### Contributing
Contributions are welcome! If you have an idea for a new feature or have found a bug, please open an issue or submit a pull request on the GitHub repository.

### License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.