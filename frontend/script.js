
const API_URL = "http://localhost:8080/api/patients";

let patients = [];

const navButtons = document.querySelectorAll("[data-page]");
const pageTitle = document.getElementById("page-title");

// Switch between Dashboard, Register and Patient Records
function showPage(pageName) {
    document.querySelectorAll(".page").forEach(function (page) {
        page.classList.add("hidden");
    });

    document.getElementById(pageName).classList.remove("hidden");

    navButtons.forEach(function (button) {
        button.classList.toggle(
            "active",
            button.dataset.page === pageName
        );
    });

    const titles = {
        dashboard: "Dashboard",
        register: "Register Patient",
        patients: "Patient Records"
    };

    pageTitle.textContent = titles[pageName];

    if (pageName === "patients") {
        loadPatients();
    }
}

// Sidebar navigation
navButtons.forEach(function (button) {
    button.addEventListener("click", function () {
        showPage(button.dataset.page);
    });
});

// Dashboard shortcuts
document.querySelectorAll("[data-go]").forEach(function (button) {
    button.addEventListener("click", function () {
        showPage(button.dataset.go);
    });
});

// Update dashboard statistics
function updateDashboard() {
    document.getElementById("patient-count").textContent =
        patients.length;

    document.getElementById("appointment-count").textContent =
        patients.filter(function (patient) {
            return patient.date && patient.time;
        }).length;
}

// Load actual patient records from Java and SQLite
async function loadPatients() {
    const message = document.getElementById("records-message");

    try {
        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error("Could not load patient records.");
        }

        patients = await response.json();

        renderPatients();

        message.textContent = "Patient records loaded from SQLite.";
    } catch (error) {
        message.textContent =
            "Could not connect to Java. Make sure the API server is running.";

        console.error(error);
    }
}

// Display and search patient records
function renderPatients() {
    const list = document.getElementById("patient-list");
    const search = document.getElementById("search").value
        .trim()
        .toLowerCase();

    const filtered = patients.filter(function (patient) {
        return String(patient.id).includes(search) ||
            patient.name.toLowerCase().includes(search);
    });

    list.innerHTML = "";

    if (filtered.length === 0) {
        list.innerHTML =
            '<tr><td colspan="6" class="empty">' +
            'No matching patient records.</td></tr>';
    } else {
        filtered.forEach(function (patient) {
            const row = document.createElement("tr");

            const values = [
                patient.id,
                patient.name,
                patient.age,
                patient.phone || "",
                (patient.date || "") + " " + (patient.time || "")
            ];

            values.forEach(function (value) {
                const cell = document.createElement("td");
                cell.textContent = value;
                row.appendChild(cell);
            });

            // Actions column
            const actions = document.createElement("td");

            // Edit button
            const editButton = document.createElement("button");
            editButton.textContent = "Edit";
            editButton.className = "primary-btn";
            editButton.style.marginRight = "6px";

            editButton.addEventListener("click", async function () {
                const name = prompt("Patient name:", patient.name);
                if (name === null) return;

                const age = prompt("Patient age:", patient.age);
                if (age === null) return;

                const phone = prompt(
                    "Phone number:", patient.phone || ""
                );
                if (phone === null) return;

                const date = prompt(
                    "Appointment date (YYYY-MM-DD):",
                    patient.date || ""
                );
                if (date === null) return;

                const time = prompt(
                    "Appointment time (HH:MM):",
                    patient.time || ""
                );
                if (time === null) return;

                if (
                    !name.trim() ||
                    age.trim() === "" ||
                    !Number.isInteger(Number(age)) ||
                    Number(age) < 0
                ) {
                    alert("Enter a valid name and age.");
                    return;
                }

                const updatedPatient = {
                    name: name.trim(),
                    age: age.trim(),
                    phone: phone.trim(),
                    date: date.trim(),
                    time: time.trim()
                };

                try {
                    const response = await fetch(
                        API_URL + "?id=" + patient.id,
                        {
                            method: "PUT",
                            body: new URLSearchParams(updatedPatient)
                        }
                    );

                    const result = await response.json();

                    if (!response.ok) {
                        throw new Error(
                            result.error || "Update failed."
                        );
                    }

                    await loadPatients();

                    document.getElementById("records-message")
                        .textContent = "Patient updated successfully!";
                } catch (error) {
                console.error("UPDATE ERROR:", error);
                alert("Update failed: " + error.message);
                }
            });

            actions.appendChild(editButton);

            // Delete button
            const deleteButton = document.createElement("button");
            deleteButton.textContent = "Delete";
            deleteButton.className = "primary-btn";
            deleteButton.style.background = "#c0392b";

            deleteButton.addEventListener("click", async function () {
                if (!confirm(
                    "Delete " + patient.name +
                    " (ID " + patient.id + ")?"
                )) {
                    return;
                }

                try {
                    const response = await fetch(
                        API_URL + "?id=" + patient.id,
                        { method: "DELETE" }
                    );

                    if (!response.ok) {
                        throw new Error("Delete failed.");
                    }

                    await loadPatients();

                    document.getElementById("records-message")
                        .textContent = "Patient deleted successfully!";
                } catch (error) {
                    alert("Delete failed. Check the Java API server.");
                    console.error(error);
                }
            });

            actions.appendChild(deleteButton);

            row.appendChild(actions);
            list.appendChild(row);
        });
    }

    updateDashboard();
}

// Search as the user types
document.getElementById("search").addEventListener(
    "input",
    renderPatients
);

// Register a new patient in SQLite
document.getElementById("patient-form").addEventListener(
    "submit",
    async function (event) {
        event.preventDefault();

        const patient = {
            name: document.getElementById("name").value.trim(),
            age: document.getElementById("age").value,
            phone: document.getElementById("phone").value.trim(),
            date: document.getElementById("date").value,
            time: document.getElementById("time").value
        };

        if (!patient.name) {
            alert("Please enter the patient's name.");
            return;
        }

        if (patient.age === "" || Number(patient.age) < 0) {
            alert("Please enter a valid age.");
            return;
        }

        try {
            const response = await fetch(API_URL, {
                method: "POST",
                body: new URLSearchParams(patient)
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.error || "Registration failed.");
            }

            document.getElementById("form-message").textContent =
                "Patient saved successfully to SQLite!";

            document.getElementById("patient-form").reset();

            await loadPatients();
            showPage("patients");

        } catch (error) {
            document.getElementById("form-message").textContent =
                "Registration failed. Check that the Java API is running.";

            console.error(error);
        }
    }
);

// Load real records when the page starts
loadPatients();
