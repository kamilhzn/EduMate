const fs = require('fs');
const origRealpathSync = fs.realpathSync;
const origLstatSync = fs.lstatSync;

fs.realpathSync = function(p, options) {
  try {
    return origRealpathSync.call(fs, p, options);
  } catch (e) {
    if (e.code === 'ENOENT') {
      // Try to resolve by walking up until we find a real directory
      let parts = p.replace(/\\/g, '/').split('/');
      // Skip the drive letter part
      let realParts = [];
      let current = '';
      
      for (let i = 0; i < parts.length; i++) {
        if (i === 0) {
          current = parts[0];
          realParts.push(current);
          continue;
        }
        let next = current + '/' + parts[i];
        try {
          let resolved = origRealpathSync.call(fs, next);
          current = resolved;
          realParts = [resolved];
        } catch (e2) {
          if (e2.code === 'ENOENT') {
            // This part doesn't exist, but maybe the rest does
            // Just append the rest of the path
            current = current + '/' + parts.slice(i).join('/');
            break;
          }
          throw e2;
        }
      }
      return current;
    }
    throw e;
  }
};

fs.lstatSync = function(p, options) {
  try {
    return origLstatSync.call(fs, p, options);
  } catch (e) {
    if (e.code === 'ENOENT') {
      // Try existence check
      try {
        return origLstatSync.call(fs, p);
      } catch (e2) {
        throw e;
      }
    }
    throw e;
  }
};