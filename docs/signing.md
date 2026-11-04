# ตั้งค่าการอัปเดตด้วยกุญแจถาวรบนมือถือ

1. เปิดไฟล์ส่วนตัว `NotePocket-GitHub-Secret.txt` ที่ได้รับจากผู้พัฒนา แล้วคัดลอกข้อความทั้งหมด
2. เปิด https://github.com/pharmdever/NotePocket/settings/secrets/actions/new
3. ช่อง Name ใส่ `ANDROID_SIGNING_BUNDLE`
4. ช่อง Secret วางข้อความจากไฟล์ แล้วกด Add secret
5. ไปที่ Actions → Build and test NotePocket → Run workflow → main → Run workflow
6. เมื่อผ่าน ดาวน์โหลด artifact `NotePocket-0.2.0-install` แตก ZIP แล้วติดตั้ง APK

กุญแจส่วนตัวและรหัสผ่านอยู่ในไฟล์สำรองส่วนตัว `NotePocket-signing-private.zip`
ห้ามอัปโหลดไฟล์นี้หรือข้อความ secret เข้า repository, issue, comment หรือส่งต่อผู้อื่น
เก็บสำรองไว้ในที่ปลอดภัยที่เจ้าของแอปเข้าถึงได้ ห้ามสร้าง key ใหม่แทนหากต้องอัปเดตแอปที่แจกไปแล้ว

`tools/restore_signing.py` ใช้ secret เพื่อสร้าง keystore เฉพาะใน temporary directory ของ runner
ตรวจสอบว่าลงนามด้วย certificate ที่กำหนด และลบไฟล์จาก runner เมื่อ workflow จบ
เพิ่ม `versionCode` ทุกครั้งที่แจกอัปเดตรุ่นใหม่

รุ่น 0.1 ใช้ debug key ที่ไม่ได้เก็บไว้ รุ่นนี้จึงใช้ package ใหม่เพื่อคงแอปเก่าไว้
อัปเดตทับได้เฉพาะรุ่น release ของ package ใหม่ที่ลงนามด้วยกุญแจถาวรเดียวกัน
