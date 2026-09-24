const express = require('express');
const cors = require('cors');
const multer = require('multer');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const uploadDir = path.join(__dirname, 'uploads');
fs.mkdirSync(uploadDir, { recursive: true });

app.use(cors());
app.use(express.json());
app.use('/files', express.static(uploadDir));

const storage = multer.diskStorage({
  destination: (_, __, cb) => cb(null, uploadDir),
  filename: (_, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
    cb(null, Date.now() + '-' + safe);
  }
});
const upload = multer({ storage });

app.get('/api/health', (_, res) => res.json({ok:true, service:'Data Upload Wallet'}));

app.get('/api/files', (_, res) => {
  const files = fs.readdirSync(uploadDir).map(name => {
    const stat = fs.statSync(path.join(uploadDir, name));
    return {name, size: stat.size, url: '/files/' + encodeURIComponent(name)};
  });
  res.json(files);
});

app.post('/api/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({error:'No file uploaded'});
  res.json({
    name:req.file.filename,
    originalName:req.file.originalname,
    size:req.file.size,
    url:'/files/' + encodeURIComponent(req.file.filename)
  });
});

app.delete('/api/files/:name', (req,res) => {
  const name = path.basename(req.params.name);
  const target = path.join(uploadDir,name);
  if (!fs.existsSync(target)) return res.status(404).json({error:'Not found'});
  fs.unlinkSync(target);
  res.json({ok:true});
});

app.listen(PORT, '0.0.0.0', () => {
  console.log('Data Wallet server listening on port ' + PORT);
});
