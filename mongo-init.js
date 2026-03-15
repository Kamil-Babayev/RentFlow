db = db.getSiblingDB('mydb');

db.createUser({
    user: 'myuser',
    pwd: 'mypassword',
    roles: [{ role: 'readWrite', db: 'mydb' }]
});

db.createCollection('mycollection');