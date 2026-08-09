const path = require('path');
const fs = require('fs');

// Change to the target directory
process.chdir('f:/JetBrains/RAG/edumate-frontend');

// Directly require npm from the known location
const npmDir = path.join('C:/Users/ASUS/AppData/Roaming/TRAE SOLO CN/ModularData/ai-agent/vm/tools/node/node_modules/npm');
const npm = require(npmDir);

npm.load({}, function(err) {
  if (err) {
    console.error('npm load failed:', err);
    process.exit(1);
  }
  npm.commands.install([], function(err, data) {
    if (err) {
      console.error('npm install failed:', err);
      process.exit(1);
    }
    console.log('npm install completed successfully');
    console.log(data);
  });
});