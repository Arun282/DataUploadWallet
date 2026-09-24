const express = require('express');
const cors = require('cors');
const multer = require('multer');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const uploadDir = path.join(__dirname, 'uploads');
const MAX_FILE_SIZE = 100 * 1024 * 1024;

fs.mkdirSync(uploadDir, { recursive: true });

app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use('/files', express.static(uploadDir, {
  maxAge: '1h',
  etag: true
}));

const storage = multer.diskStorage({
  destination: (_, __, cb) => cb(null, uploadDir),
  filename: (_, file, cb) => {
    const safe = path.basename(file.originalname).replace(/[^a-zA-Z0-9._-]/g, '_') || 'upload.bin';
    cb(null, Date.now() + '-' + safe);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: MAX_FILE_SIZE }
});

app.get('/api/health', (_, res) => {
  res.json({ ok: true, service: 'Data Upload Wallet' });
});

app.get('/api/files', (_, res) => {
  try {
    const files = fs.readdirSync(uploadDir)
      .map(name => {
        const stat = fs.statSync(path.join(uploadDir, name));
        return {
          name,
          size: stat.size,
          url: '/files/' + encodeURIComponent(name)
        };
      })
      .sort((a, b) => b.name.localeCompare(a.name));
    res.json(files);
  } catch {
    res.status(500).json({ error: 'Could not list files' });
  }
});

app.post('/api/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file uploaded' });

  res.status(201).json({
    ok: true,
    name: req.file.filename,
    originalName: req.file.originalname,
    size: req.file.size,
    url: '/files/' + encodeURIComponent(req.file.filename)
  });
});

app.delete('/api/files/:name', (req, res) => {
  const name = path.basename(req.params.name);
  const target = path.join(uploadDir, name);

  if (!fs.existsSync(target)) {
    return res.status(404).json({ error: 'Not found' });
  }

  try {
    fs.unlinkSync(target);
    res.json({ ok: true });
  } catch {
    res.status(500).json({ error: 'Could not delete file' });
  }
});

app.use((err, _req, res, _next) => {
  if (err instanceof multer.MulterError && err.code === 'LIMIT_FILE_SIZE') {
    return res.status(413).json({ error: 'File is too large. Maximum size is 100 MB.' });
  }
  console.error(err);
  res.status(500).json({ error: 'Server error' });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log('Data Wallet server listening on port ' + PORT);
});
