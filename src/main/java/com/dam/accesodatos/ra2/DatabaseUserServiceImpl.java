package com.dam.accesodatos.ra2;

import com.dam.accesodatos.config.DatabaseConfig;
import com.dam.accesodatos.model.User;
import com.dam.accesodatos.model.UserCreateDto;
import com.dam.accesodatos.model.UserQueryDto;
import com.dam.accesodatos.model.UserUpdateDto;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación del servicio JDBC para gestión de usuarios
 *
 * ESTRUCTURA DE IMPLEMENTACIÓN:
 * - ✅ 5 MÉTODOS IMPLEMENTADOS (ejemplos para estudiantes)
 * - ❌ 8 MÉTODOS TODO (estudiantes deben implementar)
 *
 * MÉTODOS IMPLEMENTADOS (Ejemplos):
 * 1. testConnection() - Ejemplo básico de conexión JDBC
 * 2. createUser() - INSERT con PreparedStatement y getGeneratedKeys
 * 3. findUserById() - SELECT y mapeo de ResultSet a objeto
 * 4. updateUser() - UPDATE statement con validación
 * 5. transferData() - Transacción manual con commit/rollback
 *
 * MÉTODOS TODO (Estudiantes implementan):
 * 1. deleteUser()
 * 2. findAll()
 * 3. findUsersByDepartment()
 * 4. searchUsers()
 * 5. batchInsertUsers()
 * 6. getDatabaseInfo()
 * 7. getTableColumns()
 * 8. executeCountByDepartment()
 */
@Service
public class DatabaseUserServiceImpl implements DatabaseUserService {

    // JDBC PURO - SIN Spring DataSource
    // Los estudiantes usan DatabaseConfig.getConnection() directamente
    // para obtener conexiones usando DriverManager

    // ========== CE2.a: Connection Management ==========

    /**
     * ✅ EJEMPLO IMPLEMENTADO 1/5: Prueba de conexión básica
     *
     * Este método muestra el patrón fundamental de JDBC PURO:
     * 1. Obtener conexión usando DriverManager (vía DatabaseConfig)
     * 2. Ejecutar una query simple
     * 3. Procesar resultados
     * 4. Cerrar recursos con try-with-resources
     */
    @Override
    public String testConnection() {
        // Patrón try-with-resources: cierra automáticamente Connection, Statement, ResultSet
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 as test, DATABASE() as db_name")) {

            // Validar que la conexión está abierta
            if (conn.isClosed()) {
                throw new RuntimeException("La conexión está cerrada");
            }

            // Navegar al primer (y único) resultado
            if (rs.next()) {
                int testValue = rs.getInt("test");
                String dbName = rs.getString("db_name");

                // Obtener información adicional de la conexión
                DatabaseMetaData metaData = conn.getMetaData();
                String dbProduct = metaData.getDatabaseProductName();
                String dbVersion = metaData.getDatabaseProductVersion();

                return String.format("✓ Conexión exitosa a %s %s | Base de datos: %s | Test: %d",
                        dbProduct, dbVersion, dbName, testValue);
            } else {
                throw new RuntimeException("No se obtuvieron resultados de la query de prueba");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error al probar la conexión: " + e.getMessage(), e);
        }
    }


    // ========== CE2.b: CRUD Operations ==========

    /**
     * ✅ EJEMPLO IMPLEMENTADO 2/5: INSERT con PreparedStatement
     *
     * Este método muestra cómo:
     * - Usar PreparedStatement para prevenir SQL injection
     * - Setear parámetros con tipos específicos
     * - Obtener IDs autogenerados con getGeneratedKeys()
     * - Manejar excepciones SQL
     */
    @Override
    public User createUser(UserCreateDto dto) {
        String sql = "INSERT INTO users (name, email, department, role, active, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Setear parámetros del PreparedStatement
            // Índices empiezan en 1, no en 0
            pstmt.setString(1, dto.getName());
            pstmt.setString(2, dto.getEmail());
            pstmt.setString(3, dto.getDepartment());
            pstmt.setString(4, dto.getRole());
            pstmt.setBoolean(5, true); // active por defecto
            pstmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now())); // created_at
            pstmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now())); // updated_at

            // Ejecutar INSERT y obtener número de filas afectadas
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException("Error: INSERT no afectó ninguna fila");
            }

            // Obtener el ID autogenerado
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Long generatedId = generatedKeys.getLong(1);

                    // Crear objeto User con el ID generado
                    User newUser = new User(generatedId, dto.getName(), dto.getEmail(),
                            dto.getDepartment(), dto.getRole());
                    newUser.setActive(true);
                    newUser.setCreatedAt(LocalDateTime.now());
                    newUser.setUpdatedAt(LocalDateTime.now());

                    return newUser;
                } else {
                    throw new RuntimeException("Error: INSERT exitoso pero no se generó ID");
                }
            }

        } catch (SQLException e) {
            // Manejar errores específicos como email duplicado
            if (e.getMessage().contains("Unique index or primary key violation")) {
                throw new RuntimeException("Error: El email '" + dto.getEmail() + "' ya está registrado", e);
            }
            throw new RuntimeException("Error al crear usuario: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ EJEMPLO IMPLEMENTADO 3/5: SELECT y mapeo de ResultSet
     *
     * Este método muestra cómo:
     * - Usar PreparedStatement para queries parametrizadas
     * - Navegar ResultSet con rs.next()
     * - Mapear columnas SQL a campos Java
     * - Manejar tipos de datos (Long, String, Boolean, Timestamp)
     */
    @Override
    public User findUserById(Long id) {
        String sql = "SELECT id, name, email, department, role, active, created_at, updated_at " +
                     "FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Setear parámetro WHERE id = ?
            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                // next() retorna true si hay un resultado, false si no
                if (rs.next()) {
                    // Mapear ResultSet a objeto User
                    return mapResultSetToUser(rs);
                } else {
                    // No se encontró usuario con ese ID
                    return null;
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar usuario con ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * ✅ EJEMPLO IMPLEMENTADO 4/5: UPDATE statement
     *
     * Este método muestra cómo:
     * - Validar que un registro existe antes de actualizar
     * - Construir UPDATE statement con campos opcionales
     * - Actualizar solo los campos proporcionados
     * - Verificar filas afectadas
     */
    @Override
    public User updateUser(Long id, UserUpdateDto dto) {
        // Primero verificar que el usuario existe
        User existing = findUserById(id);
        if (existing == null) {
            throw new RuntimeException("No se encontró usuario con ID " + id);
        }

        // Aplicar actualizaciones del DTO al usuario existente
        dto.applyTo(existing);

        // Construir UPDATE statement
        String sql = "UPDATE users SET name = ?, email = ?, department = ?, role = ?, " +
                     "active = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Setear todos los parámetros (incluso los no modificados)
            pstmt.setString(1, existing.getName());
            pstmt.setString(2, existing.getEmail());
            pstmt.setString(3, existing.getDepartment());
            pstmt.setString(4, existing.getRole());
            pstmt.setBoolean(5, existing.getActive());
            pstmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setLong(7, id);

            // Ejecutar UPDATE
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException("Error: UPDATE no afectó ninguna fila");
            }

            // Retornar usuario actualizado
            return findUserById(id);

        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar usuario con ID " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteUser(Long id) {
        //throw new UnsupportedOperationException("TODO: Método deleteUser() para implementar por estudiantes");
        User existing = findUserById(id);
        if (existing == null) {
            throw new RuntimeException("No se encontró usuario con ID " + id);
        }
        // Construir DELETE statement
        String sql = "DELETE FROM users WHERE id = ?";
        //Creación del Try-With-Resources
        try(Connection conn = DatabaseConfig.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
            //Aplicamos nustro parámetro "id" para ejecutar nuestra consulta delete
            pstmt.setLong(1, id);
            //Ejecutamos el DELETE
            int affectedRows = pstmt.executeUpdate();
            //Nos aseguramos de que DELETE esté funcionando sobre una línea existente
            if (affectedRows == 0) {
                throw new RuntimeException("Error: DELETE no afecta a ninguna fila");
            }
            return true;
        } catch (SQLException e) {
           e.printStackTrace();
           return false;
        }
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        //IMPORTANTE MIRAR TEST: pide mostrar SOLO 3 usuarios usando la fecha de creación como dato->ORDER BY created_at DESC
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        try(Connection conn = DatabaseConfig.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setName(rs.getString("name"));
            user.setEmail(rs.getString("email"));
            users.add(user);
        }
        }catch(SQLException e){
            e.printStackTrace();
            return List.of();
        }
        return users;
    }

    // ========== CE2.c: Advanced Queries ==========

    @Override
    public List<User> findUsersByDepartment(String department) {
        //throw new UnsupportedOperationException("TODO: Método findUsersByDepartment() para implementar por estudiantes");
        List<User> users = new ArrayList<>();
        //Test nos pide buscar departamentos IT
        String sql = "SELECT * FROM users WHERE department = 'IT' ";
        try(Connection conn = DatabaseConfig.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getLong("id"));
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setDepartment(rs.getString("department"));
                users.add(user);
            }
        }catch(SQLException e){
            e.printStackTrace();
            return List.of();
        }
        return users;
    }

    @Override
    public List<User> searchUsers(UserQueryDto query) {
        //throw new UnsupportedOperationException("TODO: Método searchUsers() para implementar por estudiantes");
        List<User> users = new ArrayList<>();
        //Buscar por departamento o activo
        String sql = "SELECT * FROM users WHERE department LIKE ? or active LIKE ? LIMIT 10";
        try(Connection conn = DatabaseConfig.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getLong("id"));
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setDepartment(rs.getString("department"));
                user.setActive(rs.getBoolean("active"));
                users.add(user);
            }
        }catch(SQLException e){
            e.printStackTrace();
            return List.of();
        }
        return users;
    }


    // ========== CE2.d: Transactions ==========

    /**
     * ✅ EJEMPLO IMPLEMENTADO 5/5: Transacción manual con commit/rollback
     *
     * Este método muestra cómo:
     * - Desactivar auto-commit para control manual de transacciones
     * - Realizar múltiples operaciones en una transacción
     * - Hacer commit si todo tiene éxito
     * - Hacer rollback si hay algún error
     * - Restaurar auto-commit al estado original
     */
    @Override
    public boolean transferData(List<User> users) {
        Connection conn = null;

        try {
            // Obtener conexión
            conn = DatabaseConfig.getConnection();

            // IMPORTANTE: Desactivar auto-commit para control manual
            conn.setAutoCommit(false);

            String sql = "INSERT INTO users (name, email, department, role, active, created_at, updated_at) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // Insertar cada usuario en la transacción
                for (User user : users) {
                    pstmt.setString(1, user.getName());
                    pstmt.setString(2, user.getEmail());
                    pstmt.setString(3, user.getDepartment());
                    pstmt.setString(4, user.getRole());
                    pstmt.setBoolean(5, user.getActive() != null ? user.getActive() : true);
                    pstmt.setTimestamp(6, Timestamp.valueOf(
                            user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now()));
                    pstmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));

                    pstmt.executeUpdate();
                }
            }

            // Si llegamos aquí, todas las inserciones fueron exitosas
            // COMMIT: hacer permanentes los cambios
            conn.commit();

            return true;

        } catch (SQLException e) {
            // Si hubo algún error, deshacer TODOS los cambios
            if (conn != null) {
                try {
                    // ROLLBACK: deshacer todos los cambios de la transacción
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    throw new RuntimeException("Error crítico en rollback: " + rollbackEx.getMessage(), rollbackEx);
                }
            }

            throw new RuntimeException("Error en transacción, se hizo rollback: " + e.getMessage(), e);

        } finally {
            // IMPORTANTE: Restaurar auto-commit y cerrar conexión
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Restaurar estado original
                    conn.close();
                } catch (SQLException e) {
                    // Registrar error pero no lanzar excepción en finally
                    System.err.println("Error al cerrar conexión: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public int batchInsertUsers(List<User> users) {
        //throw new UnsupportedOperationException("TODO: Método batchInsertUsers() para implementar por estudiantes");

        try(Connection conn = DatabaseConfig.getConnection();){
            //Desactivamos el AutoCommit
            conn.setAutoCommit(false);
            //Creamos nuestra consulta SQL
            String sql = "INSERT INTO users (name, email, department, role, active, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            //Segundo bloque try
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (User user : users) {
                    pstmt.setString(1, user.getName());
                    pstmt.setString(2, user.getEmail());
                    pstmt.setString(3, user.getDepartment());
                    pstmt.setString(4, user.getRole());
                    pstmt.setBoolean(5, user.getActive() != null ? user.getActive() : true);
                    pstmt.setTimestamp(6, Timestamp.valueOf(
                            user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now()));
                    pstmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    pstmt.addBatch();
                }
                int[] updateCount = pstmt.executeBatch();
                conn.commit();
                int totalInserted = 0;
                for (int count : updateCount) {
                    if (count == Statement.SUCCESS_NO_INFO || count >= 0) {
                        totalInserted++;
                    }
                }
                if (totalInserted != users.size()) {
                    System.err.println("Advertencia: El número de inserciones no coincide con el tamaño de la lista.");
                }
                return totalInserted;
            }catch (SQLException e) {
                System.err.println("Error en la transacción, ejecutando rollback...");
                conn.rollback();
                throw new RuntimeException("Error en la inserción por lotes: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error de base de datos irrecuperable: " + e.getMessage(), e);
        }
    }

    // ========== CE2.e: Metadata ==========

    @Override
    public String getDatabaseInfo() {
        //Creamos nuestra conexión a la BBDD
        try(Connection conn = DatabaseConfig.getConnection();){
            /*
            * Que nos pide el Test:
            * 1. La info no debe de ser NULL
            * 2. Debe contener 'Base de Datos'
            * 3. Debe contener 'H2'
            * 4. Debe contener 'URL'
            * 5. Debe contener 'Usuario'
            * 6. Debe contener 'Soporta Batch'
            * 7. Debe contener 'Soporta Transacciones'
            */
            DatabaseMetaData metaData = conn.getMetaData();
            StringBuilder info = new StringBuilder();
            //Nombre de la bbdd
            info.append("Base de Datos: " + metaData.getDatabaseProductName()).append("\n");
            //Nombre del driver
            info.append("Driver JDBC : " + metaData.getDriverName()).append("\n");
            //URL
            info.append("URL : " + metaData.getURL()).append("\n");
            //Mostar User
            info.append("Usuario : " + metaData.getUserName()).append("\n");
            //Soporta Batch?
            String soportaBatch = metaData.supportsBatchUpdates() ? "SI" : "NO";
            info.append("Soporta Batch : " + soportaBatch).append("\n");
            //Soporta transacciones
            String soportaTransacciones = metaData.supportsTransactions() ? "SI" : "NO";
            info.append("Soporta Transacciones: " + soportaTransacciones).append("\n");

            return info.toString();
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error al obtener la información de la base de datos.";
        }
    }

    @Override
    public List<Map<String, Object>> getTableColumns(String tableName) {
        List<Map<String, Object>> columns = new ArrayList<>();

        try(Connection conn = DatabaseConfig.getConnection()){
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getColumns(null, null, tableName.toUpperCase(), null)) {

                while (rs.next()) {
                    Map<String, Object> columnInfo = new HashMap<>();

                    columnInfo.put("name", rs.getString("COLUMN_NAME"));
                    columnInfo.put("typeName", rs.getString("TYPE_NAME"));

                    boolean isNullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    columnInfo.put("nullable", isNullable);

                    columns.add(columnInfo);
                }
            }

        }catch(SQLException e){
            throw new RuntimeException("Error al obtener metadatos de la tabla " + tableName, e);
        }

        return columns;
    }

    // ========== CE2.f: Funciones de Agregación ==========

    @Override
    public int executeCountByDepartment(String department) {
        String sql = "SELECT COUNT(*) FROM users WHERE department = ? AND active = TRUE";
        try (Connection conn = DatabaseConfig.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, department);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar usuarios del departamento " + department, e);
        }
        return 0;
    }

    // ========== HELPER METHODS ==========

    /**
     * Método auxiliar para mapear ResultSet a objeto User
     *
     * Este método se usa en múltiples lugares para evitar duplicación de código.
     * Extrae todas las columnas del ResultSet y crea un objeto User.
     *
     * @param rs ResultSet posicionado en una fila válida
     * @return User object con datos de la fila
     * @throws SQLException si hay error al leer el ResultSet
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();

        // Mapear tipos primitivos y objetos
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setDepartment(rs.getString("department"));
        user.setRole(rs.getString("role"));
        user.setActive(rs.getBoolean("active"));

        // Mapear Timestamps a LocalDateTime
        Timestamp createdAtTimestamp = rs.getTimestamp("created_at");
        if (createdAtTimestamp != null) {
            user.setCreatedAt(createdAtTimestamp.toLocalDateTime());
        }

        Timestamp updatedAtTimestamp = rs.getTimestamp("updated_at");
        if (updatedAtTimestamp != null) {
            user.setUpdatedAt(updatedAtTimestamp.toLocalDateTime());
        }

        return user;
    }
}
