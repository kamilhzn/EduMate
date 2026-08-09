const fs = require('fs');
const path = require('path');

// Test various paths
const paths = [
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN',
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN\\ModularData',
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent',
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node',
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node.exe',
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node_modules',
  'C:\\Users\\ASUS\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node_modules\\npm',
  'f:\\JetBrains\\RAG',
  'f:\\JetBrains\\RAG\\edumate-frontend',
];

for (const p of paths) {
  console.log(`Testing: ${p}`);
  console.log(`  existsSync: ${fs.existsSync(p)}`);
  try {
    console.log(`  realpathSync: ${fs.realpathSync(p)}`);
  } catch (e) {
    console.log(`  realpathSync ERROR: ${e.code} - ${e.message}`);
  }
  try {
    const stat = fs.lstatSync(p);
    console.log(`  lstatSync: isDir=${stat.isDirectory()}, isFile=${stat.isFile()}, isSymlink=${stat.isSymbolicLink()}`);
  } catch (e) {
    console.log(`  lstatSync ERROR: ${e.code} - ${e.message}`);
  }
  console.log('---');
}