angular.module('files.controllers', [])

.controller(
		'FilesController',
		function($scope) {
			// Initialize data fields
			$scope.serverTime = "Nothing Here Yet";

			// Statistics
			$scope.fileCount = 0;
			$scope.imageBytes = 0;
			$scope.storageBytes = 0;

			// Image Gallery objects
			$scope.files = [];

			// events from server handler
			$scope.handleServerEvent = function(e) {
				$scope.$apply(function() {
					var raw = JSON.parse(e.data);
					var target = raw.target;
					var data = raw.data;
					// console.log("Received data for " + target);
					if (target == "file") {
						var file = {
							id : data.id,
							fileName : data.fileName,
							size : data.size ,
							storage : data.storage ,
							ratio : data.storage * 100 / data.size,
							format : data.format,
							contentType : data.contentType
						}
						console.log("file - " + file.fileName);
						$scope.files.unshift(file);
					} else if (target == "statistics") {
						$scope.fileCount = data.files;
						$scope.imageBytes = data.size / (1024 * 1024);
						$scope.storageBytes = data.storage / (1024 * 1024);
						$scope.serverTime = data.serverTime;
					}
				});
			};

			// sse socket
			$scope.startSocket = function(uuid) {
				$scope.stopSocket();
				$scope.images = [];
				var url = "/files/sse/" + uuid
				console.log(url);
				$scope.socket = new EventSource(url);
				$scope.socket.addEventListener("message",
						$scope.handleServerEvent, false);
			};

			$scope.stopSocket = function() {
				if (typeof $scope.socket != 'undefined') {
					$scope.socket.close();
				}
			};

		});
